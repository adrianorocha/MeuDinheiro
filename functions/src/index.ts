/**
 * R65 — Cloud Functions de notificações push (FCM). EXIGEM O PLANO BLAZE (ver functions/README.md).
 * Sem elas o app segue com as notificações locais (ReceptorAvisosDiarios etc.), sem nenhuma mudança.
 *
 *  - avisarVencimentos : diária 08:00 (America/Sao_Paulo) — contas a pagar de hoje/amanhã/em atraso, 1 push por aparelho.
 *  - resumoSemanal     : domingo 18:00 — entradas/saídas da semana e maior categoria.
 *  - avisarConvite     : novo convite por e-mail em perfis/{email}/convites/{dono}.
 *  - avisarOrcamento   : escrita de despesa -> orçamento da categoria em 80%/100% (idempotente via avisosEnviados).
 */
import { setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as logger from "firebase-functions/logger";
import {
  apagarDispositivos,
  candidatosDaRaiz,
  db,
  despesasEntre,
  despesasNaoPagas,
  dispositivosDoUsuario,
  enviar,
  membrosDe,
  todosOsDispositivos,
} from "./acesso";
import { REGIAO } from "./config";
import { agruparPorRaiz, dispositivoObsoleto, dispositivosDaRaiz, filtrarPorPrefs } from "./dominio/dispositivos";
import {
  chaveAviso,
  decidirAvisoOrcamento,
  gastoDaCategoria,
  idDaMarca,
  montarAvisoOrcamento,
} from "./dominio/orcamento";
import { calcularResumoSemanal, janelaSemana, montarResumoSemanal } from "./dominio/resumoSemanal";
import { chaveMes, FUSO, inicioDoDiaMs, limitesDoMes } from "./dominio/tempo";
import { lerDespesa, lerOrcamento, type Dispositivo } from "./dominio/tipos";
import { montarAvisoVencimentos, selecionarVencimentos } from "./dominio/vencimentos";

setGlobalOptions({ region: REGIAO, maxInstances: 3 });

/** Remove aparelhos abandonados (sem renovar o token há mais de 90 dias) e devolve os que seguem válidos. */
async function semObsoletos(ds: Dispositivo[], agora: number): Promise<Dispositivo[]> {
  const obsoletos = ds.filter((d) => dispositivoObsoleto(d, agora));
  if (obsoletos.length > 0) {
    await apagarDispositivos(obsoletos).catch((e) => logger.warn("Limpeza de dispositivos falhou", { erro: String(e) }));
  }
  return ds.filter((d) => !obsoletos.includes(d));
}

export const avisarVencimentos = onSchedule({ schedule: "0 8 * * *", timeZone: FUSO, timeoutSeconds: 300 }, async () => {
  const agora = Date.now();
  const todos = await semObsoletos(await todosOsDispositivos(), agora);
  const interessados = filtrarPorPrefs(todos, "VENCIMENTOS");
  for (const [raiz, grupo] of agruparPorRaiz(interessados)) {
    try {
      const alvos = dispositivosDaRaiz(raiz, grupo, await membrosDe(raiz));
      if (alvos.length === 0) continue;
      const msg = montarAvisoVencimentos(selecionarVencimentos(await despesasNaoPagas(raiz), agora));
      if (!msg) continue;
      const r = await enviar(alvos, msg);
      logger.info("avisarVencimentos", { raiz, ...r });
    } catch (e) {
      logger.error("avisarVencimentos falhou para uma conta", { raiz, erro: e instanceof Error ? e.message : String(e) });
    }
  }
});

export const resumoSemanal = onSchedule({ schedule: "0 18 * * 0", timeZone: FUSO, timeoutSeconds: 300 }, async () => {
  const agora = Date.now();
  const interessados = filtrarPorPrefs(await todosOsDispositivos(), "RESUMO_SEMANAL");
  const inicio = inicioDoDiaMs(janelaSemana(agora).primeiro);
  for (const [raiz, grupo] of agruparPorRaiz(interessados)) {
    try {
      const alvos = dispositivosDaRaiz(raiz, grupo, await membrosDe(raiz));
      if (alvos.length === 0) continue;
      const msg = montarResumoSemanal(calcularResumoSemanal(await despesasEntre(raiz, inicio), agora));
      if (!msg) continue;
      const r = await enviar(alvos, msg);
      logger.info("resumoSemanal", { raiz, ...r });
    } catch (e) {
      logger.error("resumoSemanal falhou para uma conta", { raiz, erro: e instanceof Error ? e.message : String(e) });
    }
  }
});

/**
 * Convite por e-mail (R32). Só avisa se `perfis/{email}` já existir (o convidado já entrou no app/portal uma vez e publicou
 * "e-mail = uid"). Sem perfil não há como saber o uid => não envia (limitação documentada). O texto é genérico.
 */
export const avisarConvite = onDocumentCreated("perfis/{email}/convites/{dono}", async (event) => {
  const perfil = await db().collection("perfis").doc(event.params.email).get();
  const uid = perfil.get("uid");
  if (typeof uid !== "string" || !uid) return;
  const alvos = filtrarPorPrefs(await dispositivosDoUsuario(uid), "CONVITE");
  if (alvos.length === 0) return;
  const r = await enviar(alvos, {
    tipo: "CONVITE",
    titulo: "Você recebeu um convite",
    corpo: "Alguém quer compartilhar uma conta com você. Toque para ver.",
    extra: {},
  });
  logger.info("avisarConvite", r);
});

/**
 * Orçamento: a cada escrita de despesa NORMAL do mês corrente, confere a categoria. Idempotência: a "marca" em
 * `users/{raiz}/avisosEnviados/{chave}` é criada com `create()` (atômico) ANTES do envio; duas execuções simultâneas nunca
 * enviam duas vezes. A marca 100% também grava a de 80%. Só marca quando há aparelho elegível.
 */
export const avisarOrcamento = onDocumentWritten("users/{uid}/despesas/{id}", async (event) => {
  const depois = event.data?.after;
  if (!depois?.exists) return;
  const raiz = event.params.uid;
  const d = lerDespesa(event.params.id, depois.data() ?? {});
  if (!d || d.natureza !== "NORMAL") return;
  const agora = Date.now();
  const mes = chaveMes(d.data);
  if (mes !== chaveMes(agora)) return; // só o mês corrente

  const orcs = (await db().collection("users").doc(raiz).collection("orcamentos").get()).docs
    .map((o) => lerOrcamento(o.data()))
    .filter((o) => o !== null);
  const orc = orcs.find((o) => o.categoria.trim().toLowerCase() === d.categoria.trim().toLowerCase());
  if (!orc) return;

  const marcas = db().collection("users").doc(raiz).collection("avisosEnviados");
  const k80 = chaveAviso(orc.categoria, mes, 80);
  const k100 = chaveAviso(orc.categoria, mes, 100);
  const existentes = await db().getAll(marcas.doc(idDaMarca(k80)), marcas.doc(idDaMarca(k100)));
  const jaAvisados = new Set<string>([k80, k100].filter((_, i) => existentes[i].exists));
  if (jaAvisados.has(k100)) return; // nada mais a avisar neste mês/categoria (barato: 2 leituras)

  const { dispositivos, membros } = await candidatosDaRaiz(raiz);
  const alvos = filtrarPorPrefs(dispositivosDaRaiz(raiz, dispositivos, membros), "ORCAMENTO");
  if (alvos.length === 0) return;

  const { inicio, fimExclusivo } = limitesDoMes(mes);
  const gasto = gastoDaCategoria(await despesasEntre(raiz, inicio, fimExclusivo), orc.categoria, mes);
  const aviso = decidirAvisoOrcamento(orc, gasto, mes, jaAvisados);
  if (!aviso) return;

  try {
    await marcas.doc(idDaMarca(aviso.chave)).create({ categoria: orc.categoria.slice(0, 60), mes, limiar: aviso.limiar, criadoEm: Date.now() });
  } catch (e) {
    if ((e as { code?: number }).code === 6) return; // ALREADY_EXISTS: outra execução já avisou
    throw e;
  }
  for (const k of aviso.marcar.filter((k) => k !== aviso.chave)) {
    await marcas.doc(idDaMarca(k)).set({ categoria: orc.categoria.slice(0, 60), mes, limiar: 80, criadoEm: Date.now() }, { merge: true });
  }
  const r = await enviar(alvos, montarAvisoOrcamento(aviso));
  logger.info("avisarOrcamento", { raiz, limiar: aviso.limiar, ...r });
});
