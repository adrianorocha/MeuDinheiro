/*
 * R65 - service worker do web push (FCM). SEM dependências externas e SEM configuração do Firebase aqui:
 * o token é obtido pela página (getToken com este service worker registrado) e as mensagens chegam como "push" de DADOS.
 * Só mostra notificações genéricas (sem valores). Mantenha a lógica de texto igual a `textoDaNotificacaoWeb` em src/lib/push.ts.
 * Não faz cache nem intercepta "fetch": não interfere no restante do portal.
 */
self.addEventListener("install", function () {
  self.skipWaiting();
});

self.addEventListener("activate", function (event) {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("push", function (event) {
  var payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch {
    return;
  }
  // O FCM entrega { data: {...}, from, fcmMessageId }; aceita também o objeto "plano".
  var data = payload && payload.data ? payload.data : payload;
  var tipos = ["VENCIMENTOS", "ORCAMENTO", "RESUMO_SEMANAL", "CONVITE"];
  if (!data || tipos.indexOf(data.tipo) < 0) return;
  var titulo = String(data.titulo || "").trim().slice(0, 80);
  if (!titulo) return;
  event.waitUntil(
    self.registration.showNotification(titulo, {
      body: String(data.corpo || "").trim().slice(0, 200),
      tag: "meudinheiro-" + data.tipo,
      renotify: false,
    })
  );
});

self.addEventListener("notificationclick", function (event) {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then(function (lista) {
      for (var i = 0; i < lista.length; i++) {
        if ("focus" in lista[i]) return lista[i].focus();
      }
      return self.clients.openWindow(self.registration.scope);
    })
  );
});
