# -*- coding: utf-8 -*-
"""Gera docs/manual/manual.html e o PDF (via Edge headless). Ilustrações esquemáticas com dados fictícios."""
import html, subprocess, os
AQUI = os.path.dirname(os.path.abspath(__file__))
E = html.escape

BG="#0D1B2A"; CARD="#1B263B"; CY="#00E5FF"; GR="#69F0AE"; RD="#FF8A80"; OR="#FFB74D"; TX="#E0E1DD"; MU="#8D99AE"; PU="#B388FF"
_uid=[0]

# ---------- primitivos SVG ----------
def t(x,y,s,size=9,fill=TX,w="normal",anchor="start"):
    return f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}" font-weight="{w}" text-anchor="{anchor}" font-family="Segoe UI,Arial,sans-serif">{E(s)}</text>'
def r(x,y,w,h,fill=CARD,rx=8,stroke=None,sw=1,op=None):
    st=f' stroke="{stroke}" stroke-width="{sw}"' if stroke else ''
    o=f' fill-opacity="{op}"' if op else ''
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}"{o}{st}/>'
def call(x,y,n):
    return f'<circle cx="{x}" cy="{y}" r="9" fill="#FF5252" stroke="#fff" stroke-width="1.5"/>'+t(x,y+3.5,str(n),10,"#fff","bold","middle")
def btn(x,y,w,h,label,color=CY,txt=BG):
    return r(x,y,w,h,color,h/2)+t(x+w/2,y+h/2+3.5,label,9,txt,"bold","middle")
def field(x,y,w,label,val=""):
    return r(x,y,w,28,"#0F1E33",6,"#2B3B55")+t(x+8,y+11,label,6.5,MU)+t(x+8,y+23,val,9,TX)

def phone(inner, calls=()):
    _uid[0]+=1; cid=f"c{_uid[0]}"
    W,H=230,450
    s=f'<svg viewBox="0 0 {W} {H}" width="{W}" height="{H}" xmlns="http://www.w3.org/2000/svg">'
    s+=r(2,2,W-4,H-4,"#05080F",30,"#3A4660",3)
    s+=f'<clipPath id="{cid}"><rect x="10" y="10" width="{W-20}" height="{H-20}" rx="24"/></clipPath>'
    s+=f'<g clip-path="url(#{cid})">'+r(10,10,W-20,H-20,BG,0)+inner+'</g>'
    s+=r(85,14,60,10,"#05080F",5)
    for n,(cx,cy) in enumerate(calls,1): s+=call(cx,cy,n)
    return s+'</svg>'

def browser(inner, calls=(), active="Início", H=300):
    W=520
    s=f'<svg viewBox="0 0 {W} {H}" width="100%" xmlns="http://www.w3.org/2000/svg">'
    s+=r(1,1,W-2,H-2,"#0F1724",10,"#3A4660",2)+r(1,1,W-2,22,"#1B263B",10)+r(1,12,W-2,11,"#1B263B",0)
    for i,c in enumerate(["#FF5F57","#FEBC2E","#28C840"]): s+=f'<circle cx="{14+i*13}" cy="12" r="3.5" fill="{c}"/>'
    s+=r(80,5,260,14,"#0D1B2A",7)+t(92,15,"portal.meudinheiro (exemplo)",7,MU)
    s+=r(2,23,100,H-25,"#111C2E",0)
    s+=t(12,40,"Meu Dinheiro",8.5,CY,"bold")
    itens=["Início","Resumo","Calendário","Lançamentos","Contas","Cartões","Pendências","Orçamentos","Metas","Relatórios"]
    for i,n in enumerate(itens):
        y=50+i*min(20,(H-70)/10)
        if n==active: s+=r(7,y-3,90,16,"#1E3A52",5)
        s+=t(14,y+8,n,7.5,CY if n==active else TX,"bold" if n==active else "normal")
    s+=f'<g transform="translate(108,28)">{inner}</g>'
    for n,(cx,cy) in enumerate(calls,1): s+=call(cx,cy,n)
    return s+'</svg>'

# ---------- telas do app (dados fictícios) ----------
def tela_home():
    s=t(20,42,"Olá!",8,MU)+t(20,58,"Saldo total",8,MU)+t(20,80,"R$ 12.480,35",20,"#fff","bold")
    s+=r(172,38,16,14,CARD,4)+t(180,49,"▦",9,CY,"bold","middle")+r(194,38,16,14,CARD,4)+t(202,49,"●",9,OR,"bold","middle")
    s+=r(18,92,92,34,"#10331F",8)+t(26,106,"Entradas",7,GR)+t(26,119,"R$ 8.200",10,GR,"bold")
    s+=r(118,92,92,34,"#3A1A1A",8)+t(126,106,"Saídas",7,RD)+t(126,119,"R$ 3.915",10,RD,"bold")
    s+=r(18,136,192,66,CARD,12,CY,1)+t(28,152,"Quanto posso gastar hoje",7.5,MU)+t(202,152,"Dentro do limite",6.5,GR,"bold","end")
    s+=t(28,174,"R$ 142,00",16,"#fff","bold")+t(28,187,"restante de hoje · limite R$ 180,00",6.5,MU)+t(28,198,"Ver como foi calculado ▸",6.5,MU)
    for i,(n,c) in enumerate([("Despesa",GR),("Escanear",CY),("Investir",CY),("Transferir","#EF7354")]):
        s+=r(18+i*49,212,45,36,CARD,10)+t(40+i*49,228,"●",10,c,"normal","middle")+t(40+i*49,242,n,6.5,TX,"normal","middle")
    s+=t(20,268,"Recentes",8,MU)
    for i,(a,b,c) in enumerate([("Supermercado","Alimentação","− R$ 87,40"),("Salário","Receita","+ R$ 5.200"),("Farmácia","Saúde","− R$ 32,90")]):
        y=276+i*34
        s+=r(18,y,192,30,CARD,8)+t(28,y+13,a,8.5,TX,"bold")+t(28,y+24,b,6.5,MU)+t(202,y+18,c,8.5,GR if "+" in c else RD,"bold","end")
    s+=r(30,410,170,28,"#142238",14)
    for i,n in enumerate(["Minha Conta","Extrato","Metas"]): s+=t(65+i*50,428,n,7,CY if i==0 else MU,"bold" if i==0 else "normal","middle")
    return phone(s,[(180,34),(196,142),(40,222),(115,402)])

def tela_novo():
    s=t(20,44,"Saldo total",8,MU)+t(20,66,"R$ 12.480,35",18,"#fff","bold")+r(18,84,194,50,CARD,10)+r(18,144,194,40,CARD,10)+r(18,194,194,40,CARD,10)+r(10,10,210,430,"#000",0,op=0.45)+r(10,250,210,200,"#142238",22)+t(115,280,"Novo lançamento",12,"#fff","bold","middle")+t(115,297,"O que você quer lançar?",8,MU,"middle")
    s+=btn(30,312,170,30,"Despesa",RD,BG)+btn(30,350,170,30,"Receita",GR,BG)+btn(30,388,170,30,"Cancelar","#2B3B55",TX)
    return phone(s,[(24,327),(24,365)])

def tela_despesa():
    s=t(20,46,"Nova Despesa",14,"#fff","bold")+t(20,60,"Conta: Itaú",7.5,CY)
    s+=r(18,68,194,40,"#0F1E33",8,CY)+t(26,82,"Descreva em uma frase",6.5,MU)+t(26,98,"gastei 45 no mercado ontem",9,TX)
    s+=btn(18,114,194,20,"Preencher pelos dados da frase",CARD,CY)
    s+=field(18,142,194,"Descrição","Mercado")+field(18,174,94,"Valor (R$)","45,00")+field(118,174,94,"Data","ontem")
    s+=field(18,206,194,"Categoria","Alimentação")+field(18,238,94,"Parcelas","1x")+field(118,238,94,"Cartão","Nubank 9876")
    s+=t(20,282,"Débito: sai da conta · Crédito: vai para a fatura",6.5,MU)
    s+=btn(18,296,194,26,"Salvar",GR,BG)
    s+=t(20,342,"Recentes",7,MU)
    for i,n in enumerate(["Padaria 12,50","Uber 23,90","Mercado 87,40"]): s+=r(18+i*66,350,62,20,CARD,10)+t(49+i*66,363,n,6,TX,"normal","middle")
    s+=t(20,392,"Escanear cupom",8,CY)
    return phone(s,[(214,78),(24,148),(214,190),(124,252),(214,310)])

def tela_cartao():
    s=t(20,44,"Cartões",14,"#fff","bold")
    s+=r(18,54,194,100,"#2A1B5E",14)+t(30,74,"Nubank",10,"#fff","bold")+t(30,90,"LIMITE",6,MU)+t(30,102,"R$ 8.000",10,"#fff","bold")
    s+=t(30,138,"•••• 9876",11,"#fff","bold")+t(160,126,"VENCTO",5.5,MU)+t(160,140,"25",11,"#fff","bold")
    s+=t(20,178,"FATURA DE NOVEMBRO",8,MU,"bold")+t(202,178,"Fecha dia 18",6.5,MU,"normal","end")
    s+=r(18,186,194,70,CARD,10)+t(28,202,"Vencimento",8,MU)+t(202,202,"25 de novembro",8,"#fff","bold","end")
    s+=t(28,220,"Fechamento",8,MU)+t(202,220,"18 de novembro",8,"#fff","bold","end")+t(28,240,"Total pendente",8,MU)+t(202,240,"R$ 2.512,40",9,RD,"bold","end")
    s+=btn(18,266,94,26,"Pagar fatura",CY,BG)+btn(118,266,94,26,"Nova compra",CARD,CY)
    s+=r(18,304,194,40,CARD,10)+t(28,320,"Compras desta fatura",7.5,MU)+t(28,335,"Streaming · Restaurante · Farmácia",7,TX)
    return phone(s,[(214,62),(24,200),(30,279),(124,279)])

def tela_pagar():
    s=t(20,44,"Pagar fatura",13,"#fff","bold")
    s+=t(20,64,"Selecionar tudo",7.5,CY)+t(95,64,"Só esta fatura",7.5,CY)+t(165,64,"Limpar",7.5,MU)
    for i,(m,v,c) in enumerate([("11/2026","vence 25/11 • 8/8 itens",True),("12/2026","vence 25/12 • 0/3 itens",False)]):
        y=76+i*40
        s+=r(18,y,194,34,CARD,8)+t(28,y+15,m,9,"#fff","bold")+t(28,y+27,v,6.5,MU)+r(184,y+9,16,16,CY if c else "#2B3B55",4)
    s+=r(18,168,194,92,"#0F1E33",10)
    for i,(a,b) in enumerate([("Selecionado","R$ 2.512,40"),("Limite restaurado","R$ 2.512,40"),("Saldo da conta","R$ 6.100,00"),("Saldo da conta após","R$ 3.587,60")]):
        s+=t(28,188+i*20,a,7.5,MU)+t(202,188+i*20,b,8.5,"#fff","bold","end")
    s+=btn(18,276,194,28,"Pagar",GR,BG)
    s+=t(115,322,"O pagamento é uma operação única: debita a conta,",6.5,MU,"normal","middle")+t(115,332,"quita os itens e devolve o limite.",6.5,MU,"normal","middle")
    return phone(s,[(70,56),(214,92),(24,224),(24,290)])

def tela_extrato():
    s=t(20,44,"Extrato",14,"#fff","bold")+t(202,44,"2026",9,CY,"bold","end")
    xs=[18,62,118]; ws=[40,52,84]
    for i,(n,a) in enumerate([("Tudo",1),("Conjunta",0),("Minhas despesas",0)]):
        s+=r(xs[i],54,ws[i],18,CY if a else CARD,9)+t(xs[i]+ws[i]/2,66,n,6.5,BG if a else TX,"bold","middle")
    s+=t(20,92,"#reforma   #viagem",7,PU)
    for i,(a,b,c) in enumerate([("Mercado","12/11 · Alimentação","− 87,40"),("Salário","05/11 · Receita","+ 5.200,00"),("Netflix","03/11 · Assinaturas","− 55,90"),("Transferência","02/11 · Entre contas","− 500,00"),("Combustível","01/11 · Transporte","− 210,00"),("Pix recebido","30/10 · Receita","+ 120,00")]):
        y=102+i*38
        s+=r(18,y,194,34,CARD,8)+t(28,y+15,a,8.5,"#fff","bold")+t(28,y+27,b,6.5,MU)+t(202,y+20,c,8.5,GR if "+" in c else RD,"bold","end")
    return phone(s,[(200,34),(40,84),(24,128)])

def tela_metas():
    s=t(20,44,"Metas",14,"#fff","bold")
    s+=r(18,56,194,110,CARD,12)+t(28,74,"Viagem de férias",9.5,"#fff","bold")
    s+='<circle cx="62" cy="108" r="26" fill="none" stroke="#2B3B55" stroke-width="7"/><circle cx="62" cy="108" r="26" fill="none" stroke="#69F0AE" stroke-width="7" stroke-dasharray="101 163" transform="rotate(-90 62 108)"/>'
    s+=t(62,112,"62%",9,"#fff","bold","middle")+t(110,96,"Guardado",6.5,MU)+t(110,108,"R$ 3.100",9.5,GR,"bold")+t(110,124,"Objetivo",6.5,MU)+t(110,136,"R$ 5.000",9.5,"#fff","bold")
    s+=btn(28,142,174,18,"Realizar Aporte",BG,GR)
    s+=r(18,176,194,50,CARD,12)+t(28,194,"Reserva de emergência",9.5,"#fff","bold")+r(28,204,174,8,"#2B3B55",4)+r(28,204,70,8,CY,4)+t(28,222,"R$ 7.000 de R$ 18.000",6.5,MU)
    s+=r(176,390,38,38,CY,19)+t(195,416,"+",22,BG,"bold","middle")
    return phone(s,[(214,68),(24,151),(176,386)])

def tela_hub():
    s=t(20,44,"Ferramentas",14,"#fff","bold")+r(18,54,194,22,"#0F1E33",11,"#2B3B55")+t(28,69,"Buscar lançamentos, contas, metas",6.5,MU)
    for i,(a,b) in enumerate([("Relatórios","PDF, PNG ou CSV"),("Calendário financeiro","Vencimentos e saldo projetado"),("Resumo inteligente","O que mudou no mês"),("Planejamento","Reserva, 50/30/20, metas"),("Regras de categoria","Categoriza pela descrição"),("Pessoas e rateios","Quem te deve"),("Imposto de Renda","Triagem da declaração"),("Lixeira","30 dias para restaurar")]):
        y=84+i*40
        s+=r(18,y,194,35,CARD,8)+t(28,y+15,a,8.5,"#fff","bold")+t(28,y+27,b,6.5,MU)+t(202,y+22,"›",12,CY,"bold","end")
    return phone(s,[(214,62),(214,112),(214,232)])

def tela_limite():
    s=t(20,44,"Início",10,MU)
    s+=r(14,56,202,150,CARD,14,GR,1.2)+t(26,74,"Quanto posso gastar hoje",8,MU)+t(204,74,"Dentro do limite",7,GR,"bold","end")
    s+=t(26,100,"R$ 142,00",20,"#fff","bold")+t(26,114,"restante de hoje · limite R$ 180,00",7,MU)
    s+=t(26,130,"Você pode gastar R$ 180,00 por dia até 30/11.",7,TX)
    s+=t(26,148,"Saldo 4.100 + receitas 13.000 (estimadas 13.000)",6.5,MU)+t(26,158,"− compromissos 5.600 até 30/11 = 11.500 ÷ 20 dia(s)",6.5,MU)
    s+=t(26,178,"Período: fim do mês ▸ (toque p/ alternar)",6.5,MU)+t(206,178,"Calendário ▸",6.5,CY,"normal","end")
    s+=t(26,194,"Renda estimada pelo histórico: ligada ▸",6.5,MU)
    return phone(s,[(214,96),(214,142),(24,182),(24,198)])

def tela_pend():
    s=t(20,44,"Pendências",14,"#fff","bold")
    for i,(a,b,c,k) in enumerate([("Aluguel","vence 10/11 • Itaú","− 1.800,00",RD),("Internet","vence 12/11 • Itaú","− 119,90",RD),("Freelance","prevista 15/11 • Itaú","+ 900,00",GR)]):
        y=58+i*70
        s+=r(18,y,194,62,CARD,10)+t(28,y+18,a,9.5,"#fff","bold")+t(28,y+31,b,6.5,MU)+t(202,y+20,c,9,k,"bold","end")+btn(130,y+36,72,20,"Dar Baixa",CY,BG)
    return phone(s,[(130,92),(214,78)])

def tela_transf():
    s=t(20,44,"Transferência entre Contas",11,"#fff","bold")
    s+=field(18,60,194,"De","Itaú")+t(115,106,"⇅",14,CY,"bold","middle")+field(18,112,194,"Para","Nubank")
    s+=field(18,152,194,"Valor","R$ 500,00")+field(18,186,194,"Data","hoje")
    s+=t(20,232,"Data futura? O app pergunta:",7,MU)+r(18,240,194,28,"#2A2410",8,OR)+t(28,258,"Agendar transferência?",8.5,OR,"bold")
    s+=btn(18,284,194,28,"Confirmar",GR,BG)
    return phone(s,[(214,74),(214,126),(214,166),(214,254)])

# ---------- telas do portal ----------
def p_dash():
    s=t(0,12,"Início",11,"#fff","bold")
    for i,(a,b,c) in enumerate([("Saldo total","R$ 12.480","#fff"),("Entradas","R$ 8.200",GR),("Saídas","R$ 3.915",RD),("Limite hoje","R$ 142",CY)]):
        s+=r(i*102,20,96,44,CARD,8)+t(i*102+8,34,a,7,MU)+t(i*102+8,54,b,11,c,"bold")
    s+=r(0,72,250,120,CARD,8)+t(8,86,"Receitas × Despesas (6 meses)",7.5,MU)
    for i,(a,b) in enumerate([(60,40),(65,45),(58,50),(70,42),(62,38),(75,44)]):
        s+=r(18+i*38,180-a,12,a,GR,2)+r(32+i*38,180-b,12,b,RD,2)
    s+=r(258,72,150,120,CARD,8)+t(266,86,"Por categoria",7.5,MU)
    s+='<circle cx="333" cy="140" r="32" fill="none" stroke="#00E5FF" stroke-width="14" stroke-dasharray="70 131"/><circle cx="333" cy="140" r="32" fill="none" stroke="#B388FF" stroke-width="14" stroke-dasharray="50 151" stroke-dashoffset="-70"/><circle cx="333" cy="140" r="32" fill="none" stroke="#FFB74D" stroke-width="14" stroke-dasharray="40 161" stroke-dashoffset="-120"/>'
    s+=r(0,200,408,60,CARD,8)+t(8,214,"Alertas de orçamento e vencimentos",7.5,MU)+t(8,232,"Alimentação em 86% do orçamento",7.5,OR)+t(8,246,"Fatura Nubank vence em 3 dias",7.5,RD)
    return browser(s,[(118,46),(250,100),(420,218)],"Início",290)

def p_lanc():
    s=t(0,12,"Lançamentos",11,"#fff","bold")+btn(330,2,78,16,"+ Novo (N)",CY,BG)
    s+=r(0,24,408,20,"#0F1E33",6,"#2B3B55")+t(8,37,"Buscar…   Período ▾   Conta ▾   Categoria ▾   Tipo ▾",7,MU)
    hdr=["Data","Descrição","Categoria","Conta","Valor"]; xs=[8,52,160,250,400]
    s+=r(0,50,408,16,"#16243A",4)
    for h,x in zip(hdr,xs): s+=t(x,61,h,7,MU,"bold","end" if h=="Valor" else "start")
    for i,row in enumerate([("12/11","Mercado","Alimentação","Itaú","− 87,40"),("05/11","Salário","Receita","Itaú","+ 5.200,00"),("03/11","Netflix","Assinaturas","Nubank","− 55,90"),("01/11","Combustível","Transporte","Itaú","− 210,00"),("30/10","Pix recebido","Receita","Itaú","+ 120,00")]):
        y=72+i*24
        s+=r(0,y,408,22,CARD if i%2==0 else "#17233A",4)
        for v,x in zip(row,xs): s+=t(x,y+14,v,7.5,(GR if "+" in v else RD) if x==400 else TX,"normal","end" if x==400 else "start")
    s+=t(0,208,"Clique em uma linha para editar; o menu da linha duplica, divide ou exclui (vai para a Lixeira).",7,MU)
    return browser(s,[(432,34),(500,66),(500,112)],"Lançamentos",240)

def p_conc():
    s=t(0,12,"Conciliação de extrato",11,"#fff","bold")
    for i,n in enumerate(["1 Arquivo","2 Mapear colunas","3 Revisar","4 Aplicar"]):
        s+=r(i*102,22,96,20,CY if i==0 else CARD,10)+t(i*102+48,35,n,7.5,BG if i==0 else TX,"bold","middle")
    s+=r(0,52,408,70,"#0F1E33",10,CY,1)+t(204,84,"Arraste o arquivo OFX ou CSV aqui",9,TX,"bold","middle")+t(204,98,"ou clique em Escolher arquivos de extrato",7.5,MU,"middle")
    s+=r(0,132,408,70,CARD,8)+t(8,146,"Modo de conciliação",7.5,MU)+t(8,162,"(•) Conciliar automáticos (valor exato, data próxima, candidato único)",7.5,TX)+t(8,176,"( ) Criar lançamentos que só estão no extrato",7.5,TX)+t(8,190,"Janela de datas (± dias): 3",7.5,MU)
    return browser(s,[(112,50),(500,92),(106,164)],"Início",240)

def p_nav():
    s=''
    grupos=[("Visão geral",["Início","Resumo","Calendário"]),("Movimentação",["Lançamentos","Contas","Cartões","Pendências","Recorrências","Conciliação"]),("Planejar",["Orçamentos","Metas","Planejamento","Ferramentas","Investimentos","Dívidas","Projetos"]),("Análises",["Relatórios","Imposto de Renda"]),("Organização",["Categorias","Regras","Pessoas","Membros"]),("Sistema",["Lixeira","Backups","Configurações"])]
    for gi,(g,its) in enumerate(grupos):
        cx=(gi%3)*172; cy=(gi//3)*150
        h=24+len(its)*16
        s+=r(cx,cy,164,h,CARD,10)+t(cx+10,cy+16,g.upper(),7.5,CY,"bold")
        for i,n in enumerate(its): s+=t(cx+10,cy+32+i*16,"• "+n,8.5,TX)
    return f'<svg viewBox="0 0 520 290" width="100%" xmlns="http://www.w3.org/2000/svg">{s}</svg>'

# ---------- montagem ----------
def passos(lst): return "<ol class='steps'>"+"".join(f"<li>{x}</li>" for x in lst)+"</ol>"
def dica(txt,tipo="dica"):
    ic={"dica":"Dica","aviso":"Atenção","nota":"Nota"}[tipo]
    return f"<div class='box {tipo}'><b>{ic}:</b> {txt}</div>"
def func(titulo, onde, texto, steps, ilus=None, notas=""):
    h=f"<section class='func'><h3>{titulo}</h3><p class='onde'>Onde: {onde}</p><p>{texto}</p>"
    if ilus: h+=f"<div class='dois'><div class='ilus'>{ilus}</div><div>{passos(steps)}{notas}</div></div>"
    else: h+=passos(steps)+notas
    return h+"</section>"
def legenda(txt): return f"<p class='leg'>{txt}</p>"

P=[]
P.append(f"""<section class='capa'><div class='logo'>R$</div><h1>Meu Dinheiro</h1><h2>Manual do Usuário</h2>
<p>Guia ilustrado com passo a passo do aplicativo Android e do portal web</p><div class='capa-fone'>{tela_home()}</div>
<p class='ver'>Novembro/2026 · Telas esquemáticas com dados fictícios</p></section>""")

P.append("""<section class='pg'><h2>Sumário</h2><div class='toc'>
<h4>Parte 1 — Conhecendo o sistema</h4><ol><li>O que é o Meu Dinheiro</li><li>App × Portal: quando usar cada um</li><li>Conceitos básicos</li><li>Onde seus dados ficam e sincronização</li></ol>
<h4>Parte 2 — Aplicativo Android</h4><ol start="5"><li>Primeiro acesso e login</li><li>Tela inicial</li><li>“Quanto posso gastar hoje”</li><li>Lançar uma despesa (formulário, frase/voz, cupom e boleto)</li><li>Lançar uma receita e ajustar saldo</li><li>Cartões e faturas</li><li>Pagar uma fatura</li><li>Transferência entre contas</li><li>Pendências e “Dar baixa”</li><li>Extrato e filtros</li><li>Metas e investimentos</li><li>Central de Ferramentas</li><li>Captura de notificações e importação de extratos</li><li>Backups, Lixeira e privacidade</li></ol>
<h4>Parte 3 — Portal web</h4><ol start="19"><li>Acesso, modos Local e Nuvem</li><li>Navegação do portal</li><li>Início (painel)</li><li>Lançamentos e lançamento rápido</li><li>Contas, Cartões, Pendências e Recorrências</li><li>Conciliação de extrato (OFX/CSV)</li><li>Orçamentos, Metas e Planejamento</li><li>Ferramentas financeiras</li><li>Relatórios e Imposto de Renda</li><li>Categorias, Regras, Pessoas e Membros</li><li>Lixeira, Backups e Configurações</li></ol>
<h4>Parte 4 — Apoio</h4><ol start="30"><li>Boas práticas e rotina recomendada</li><li>Perguntas frequentes e solução de problemas</li></ol>
</div></section>""")

P.append(f"""<section class='pg'><h2>Parte 1 — Conhecendo o sistema</h2>
<h3>1. O que é o Meu Dinheiro</h3>
<p>O Meu Dinheiro é um sistema de finanças pessoais com dois “rostos” que trabalham juntos: um <b>aplicativo Android</b>, para o dia a dia (lançar um gasto na hora, escanear um cupom, ver quanto ainda pode gastar), e um <b>portal web</b>, para análises, importação de extratos, relatórios e ajustes em volume.</p>
<p>Com ele você controla contas bancárias, cartões de crédito e débito, receitas e despesas, parcelas, recorrências, metas, investimentos, dívidas e orçamentos — com as mesmas regras de cálculo no app e no portal.</p>
<h3>2. App × Portal: quando usar cada um</h3>
<table class='tb'><tr><th>Preciso…</th><th>App</th><th>Portal</th></tr>
<tr><td>Lançar um gasto na hora (frase/voz, cupom, boleto)</td><td>ideal</td><td>sim (tecla <b>N</b>)</td></tr>
<tr><td>Ver “quanto posso gastar hoje”</td><td>sim</td><td>sim</td></tr>
<tr><td>Importar extrato OFX</td><td>sim (conta)</td><td>sim (conta e fatura)</td></tr>
<tr><td>Importar CSV e conciliar com mapeamento de colunas</td><td>—</td><td>sim</td></tr>
<tr><td>Relatórios com filtros; exportar PDF/PNG/CSV</td><td>sim</td><td>sim</td></tr>
<tr><td>Capturar notificações do banco como sugestões</td><td>sim (opcional)</td><td>—</td></tr>
<tr><td>Escanear QR da nota fiscal (NFC-e) ou boleto</td><td>sim</td><td>—</td></tr>
<tr><td>Compartilhar a conta com outra pessoa</td><td>convite</td><td>página Membros</td></tr>
<tr><td>Funcionar sem internet</td><td>sim</td><td>modo Local</td></tr></table>
<div class='box nota'><b>Sobre as ilustrações:</b> as telas deste manual são <b>desenhos esquemáticos com dados fictícios</b>. Cores, ícones e posições podem variar um pouco na sua versão; os nomes de botões e campos seguem os do sistema. Os números vermelhos nas imagens remetem aos elementos citados nos passos.</div>
<h3>3. Conceitos básicos</h3>
<table class='tb'><tr><th style='width:26%'>Termo</th><th>Significado</th></tr>
<tr><td><b>Conta</b></td><td>Onde o dinheiro está (conta corrente, carteira…). O saldo é calculado a partir do extrato.</td></tr>
<tr><td><b>Lançamento</b></td><td>Uma movimentação: despesa, receita ou transferência (um par de lançamentos entre contas).</td></tr>
<tr><td><b>Pendência</b></td><td>Lançamento com data futura ou ainda não pago/recebido. Você “dá baixa” quando acontece.</td></tr>
<tr><td><b>Cartão de crédito</b></td><td>A compra não sai da conta na hora: vai para a <b>fatura</b>, que fecha em um dia e vence em outro.</td></tr>
<tr><td><b>Cartão de débito</b></td><td>A compra sai direto da conta (entra como paga, se a data já chegou).</td></tr>
<tr><td><b>Cartão virtual</b></td><td>Ligado a um cartão físico; compartilha o mesmo limite.</td></tr>
<tr><td><b>Fatura</b></td><td>Conjunto das compras de um ciclo do cartão, pago de uma só vez no vencimento.</td></tr>
<tr><td><b>Recorrência</b></td><td>Lançamento que se repete (aluguel, assinatura), gerado automaticamente.</td></tr>
<tr><td><b>Orçamento</b></td><td>Teto mensal por categoria; o sistema avisa em 80% e 100%.</td></tr>
<tr><td><b>Meta</b></td><td>Objetivo com valor e prazo; o aporte sai de uma conta (não “cria” dinheiro).</td></tr></table>
<h3>4. Onde seus dados ficam e sincronização</h3>
<table class='tb'><tr><th>Modo</th><th>Como funciona</th></tr>
<tr><td><b>Somente neste celular</b> (app) / <b>Local</b> (portal)</td><td>Tudo fica no aparelho/navegador. Nenhuma chamada de rede. Não sincroniza.</td></tr>
<tr><td><b>Nuvem (Firebase)</b></td><td>Dados na sua conta na nuvem; app e portal sincronizam em tempo real. Em conflito, a nuvem prevalece. O app funciona offline e sincroniza depois.</td></tr></table>
{dica("No app, a escolha está em <b>Configurações → Armazenamento de dados</b>, onde também há “Sincronizar agora”, “Enviar tudo” e “Baixar tudo”. Estes dois últimos substituem um lado pelo outro: use com cuidado.","aviso")}
</section>""")

P.append("<section class='pg'><h2>Parte 2 — Aplicativo Android</h2>")
P.append(func("5. Primeiro acesso e login","tela <b>Bem-vindo</b>","Ao abrir o app você entra com seus dados. É possível usar biometria (digital/rosto) para entrar mais rápido e para recuperar o acesso.",
 ["Abra o app. Na tela <b>Bem-vindo</b>, informe e-mail e senha e toque em <b>ENTRAR</b>.","No primeiro uso, faça o cadastro de usuário e crie sua primeira <b>conta bancária</b> (banco, agência/conta e saldo inicial).","Para entrar com biometria, ative-a nas configurações e use o sensor quando o app pedir.","Esqueceu a senha? Use <b>Recuperar Acesso</b>: confirme sua identidade com a biometria e redefina a senha em Perfil."],
 notas=dica("A senha é guardada criptografada e nunca é exibida em texto; por isso a recuperação exige biometria.")))
P.append(func("6. Tela inicial","aba <b>Minha Conta</b> (menu inferior: Minha Conta · Extrato · Metas)","Reúne saldo, resumo do mês, o card “Quanto posso gastar hoje”, os atalhos de ação e os lançamentos recentes.",
 ["No topo veja o <b>saldo</b>, o ícone de <b>Ferramentas</b> (1), o de <b>Notificações</b> e os totais de <b>Entradas</b> e <b>Saídas</b> do mês.","Toque no card <b>Quanto posso gastar hoje</b> (2) para ver o cálculo (capítulo 7).","Use os atalhos (3): <b>Despesa</b>, <b>Escanear</b>, <b>Investir</b> e <b>Transferir</b>.","Navegue pelo menu inferior (4): <b>Minha Conta</b>, <b>Extrato</b> e <b>Metas</b>.","Role para ver os lançamentos recentes, orçamentos e agendamentos ativos."],tela_home()))
P.append("</section>")

P.append("<section>"+func("7. “Quanto posso gastar hoje”","card na tela inicial","Mostra quanto você ainda pode gastar hoje sem comprometer contas e faturas futuras. A conta é: <b>saldo + receitas previstas − compromissos (faturas, contas a pagar, recorrências)</b> até o fim do período, dividido pelos dias restantes.",
 ["Leia o valor grande (1): é o <b>restante de hoje</b>; ao lado aparece o <b>limite diário</b>. A etiqueta indica o estado: <b>Dentro do limite</b>, <b>Perto do limite</b>, <b>Acima do limite</b> ou <b>Mês no vermelho</b>.","Toque em <b>Ver como foi calculado ▸</b> (2) para ver saldo, receitas (inclusive a parte estimada), compromissos e dias restantes.","Toque em <b>Período</b> (3) para alternar entre <i>fim do mês</i> e <i>até a próxima receita</i>.","Toque em <b>Renda estimada pelo histórico</b> (4) para ligar ou desligar a estimativa (veja a atenção abaixo).","Toque em <b>Calendário ▸</b> para ver vencimentos e o saldo projetado dia a dia."],tela_limite(),
 dica("Se o seu salário/pró-labore não está cadastrado como pendência ou recorrência, o app não sabe que ele vai entrar e o limite pode ficar em zero. Com a <b>renda estimada</b> ligada, o app usa a mediana dos últimos 3 meses de receitas pagas (sem empréstimos, financiamentos, estornos e reembolsos), no dia em que costumam cair. É uma estimativa: se a renda atrasar ou vier menor, o número será otimista. Esta opção existe só no app; o portal ainda não a aplica, então os valores podem divergir enquanto ela estiver ligada.","aviso"))+"</section>")

P.append("<section>"+func("8. Lançar uma despesa","Início → <b>Despesa</b> (ou <b>Novo Lcto</b> → Despesa)","O formulário completo aceita descrição, valor, data, categoria, parcelas e cartão. Você pode preenchê-lo digitando (ou ditando) uma frase.",
 ["Toque em <b>Despesa</b> e confirme a conta no topo (“Conta: …”).","<b>Opção rápida (1):</b> em <i>Descreva em uma frase</i>, escreva ou dite, por exemplo “gastei 45 no mercado ontem”, e toque em <b>Preencher pelos dados da frase</b>. O app mostra “Entendi: …” e preenche valor, data, categoria e destino.","Se a frase citar cartão ou débito (“no débito do Itaú”), o app escolhe o cartão e a modalidade. Pendências aparecem em laranja: escolha o cartão/conta na lista.","Revise <b>Descrição</b> (2), <b>Valor</b> (aceita outras moedas, com <b>Cotação</b>), <b>Data</b> e <b>Categoria</b> (3).","Se for parcelado, informe <b>Parcelas</b> (e <b>Parcela atual</b>, se a compra já está em andamento) (4).","Escolha o <b>Cartão</b>: <b>débito</b> sai da conta; <b>crédito</b> vai para a fatura; cartão virtual aparece como “Virtual de …”.","Toque em <b>Salvar</b> (5). Os lançamentos mais usados ficam em <b>Recentes</b> para repetir com 1 toque."],tela_despesa(),
 dica("Compra em dinheiro, Pix ou débito com data que já chegou entra <b>paga</b>. Compra no crédito fica <b>pendente</b> até o pagamento da fatura.","nota"))+
 func("Escanear cupom fiscal e boleto","<b>Escanear cupom</b> no formulário · atalho <b>Escanear</b> na tela inicial","A câmera lê o QR Code da nota fiscal (NFC-e) e consulta o valor total no site da SEFAZ, ou lê o código de barras de um boleto.",
 ["Toque em <b>Escanear</b> (boleto) ou em <b>Escanear cupom</b> (NFC-e) e permita o uso da câmera.","Enquadre o QR Code/código de barras na moldura até a leitura.","No cupom, aguarde <i>Consultando o valor no site da SEFAZ…</i>. O <b>Valor total</b> aparece; se a consulta falhar, digite o valor ou use <b>Abrir a nota no navegador</b>.","Toque em <b>Usar</b> para levar o valor ao formulário (ou <b>Escanear de novo</b>).","No boleto, confira os dados na tela de confirmação antes de salvar."])+"</section>")

P.append("<section>"+func("9. Lançar uma receita e ajustar saldo","<b>Novo Lcto</b> → Receita · Depósito Rápido · <b>Ajustar saldo da conta</b>","Registre entradas (salário, Pix recebido…) e acerte o saldo do sistema com o do banco.",
 ["Toque em <b>Novo Lcto</b> (1) e escolha <b>Receita</b> (2).","Preencha <b>Descrição</b>, <b>Valor</b>, <b>Categoria</b> e <b>Data</b>.","Se o dinheiro veio de alguém, escolha em <b>De quem veio (pessoa)</b>; senão, “Ninguém”.","Marque <b>Já recebido</b> se o dinheiro já está na conta; desmarcado, vira pendência (a receber).","Toque em <b>Salvar</b>. Para um depósito simples use o <b>Depósito Rápido</b> (valor e CONFIRMAR).","Saldo diferente do banco? Use <b>Ajustar saldo da conta</b>: informe o <b>Saldo real no banco</b> e uma <b>Observação</b>; o app lança a diferença."],tela_novo())+
 ""+func("10. Cartões e faturas","tela <b>Cartões</b>","Cada cartão mostra limite, final, vencimento e a fatura do mês. As faturas respeitam o dia de fechamento: compras depois do fechamento caem na fatura seguinte.",
 ["Abra <b>Cartões</b> e adicione um cartão: nome, final, <b>limite</b>, <b>dia de fechamento</b>, <b>dia de vencimento</b>, <b>conta vinculada</b> e modalidade (crédito, débito ou múltiplo).","Para um cartão virtual, escolha em <b>Compartilha o saldo do cartão físico</b> o cartão principal; “Nenhum” cria um físico independente.","Toque no cartão (1) para ver a <b>FATURA</b> do mês (2): Vencimento, Fechamento e Total pendente. Use “Ir para a fatura …” para outros meses.","<b>Pagar fatura</b> (3) e <b>Nova compra</b> (4) ficam logo abaixo.","O quadro <i>Limite compartilhado</i> mostra Total, Usado e Disponível do grupo (físico + virtuais)."],tela_cartao())+"</section>")

P.append("<section>"+func("11. Pagar uma fatura","Cartões → <b>Pagar fatura</b>","O pagamento é uma operação única: debita a conta, marca os itens como pagos e devolve o limite do cartão.",
 ["Na fatura, toque em <b>Pagar fatura</b>.","Escolha o que pagar (1): <b>Selecionar tudo</b>, <b>Só esta fatura</b> ou marque faturas/itens; <b>Limpar</b> desmarca tudo.","Marque as faturas desejadas (2).","Confira o resumo (3): <b>Selecionado</b>, <b>Limite restaurado</b>, <b>Saldo da conta</b> e <b>Saldo da conta após</b>. Se ficar negativo, o app avisa em amarelo.","Toque em <b>Pagar</b> (4). Se a fatura já foi paga fora do app, use <b>Marcar como paga</b>: quita os itens <u>sem</u> mexer no saldo da conta."],tela_pagar())+
 ""+func("12. Transferência entre contas","atalho <b>Transferir</b> (tela inicial)","Move dinheiro de uma conta para outra. São dois lançamentos ligados, que não contam como receita nem despesa.",
 ["Toque em <b>Transferir</b>.","Escolha a conta de <b>origem</b> (1) e a de <b>destino</b> (2).","Informe o <b>Valor</b> (3) e a <b>Data</b>.","Se a data for futura, o app pergunta <b>Agendar transferência?</b> (4); confirme para criar o agendamento.","Toque em <b>Confirmar</b>."],tela_transf())+"</section>")

P.append("<section>"+func("13. Pendências e “Dar baixa”","Pendências (pela tela inicial ou pelo aviso de vencimento)","Lista o que ainda vai acontecer: contas a pagar, recebimentos previstos e itens de cartão. Ao pagar ou receber, dê baixa.",
 ["Abra <b>Pendências</b>.","Localize o item: descrição, conta, data e valor aparecem no cartão (2).","Toque em <b>Dar Baixa</b> (1) e confirme.","O saldo da conta é atualizado e o item sai da lista."],tela_pend(),
 dica("O app envia avisos de vencimento (e, opcionalmente, do que já venceu, 1 vez por dia). Ajuste em Configurações, no Motor de alertas.","nota"))+
 ""+func("14. Extrato e filtros","aba <b>Extrato</b>","Histórico de movimentações do ano escolhido.",
 ["Abra a aba <b>Extrato</b> e escolha o <b>ano</b> (1).","Use os filtros (3): <b>Tudo</b>, <b>Conjunta</b> (conta compartilhada) e <b>Minhas despesas</b>.","Toque em uma <b>#tag</b> (2) para ver só aquele assunto (ex.: #reforma).","Toque em um lançamento para editar, repetir/duplicar, dividir ou excluir (vai para a Lixeira)."],tela_extrato())+"</section>")

P.append("<section>"+func("15. Metas e investimentos","aba <b>Metas</b> · atalho <b>Investir</b>","Metas guardam dinheiro para um objetivo; o aporte <b>sai de uma conta</b>. Investimentos registram ativos e seu valor atual.",
 ["Na aba <b>Metas</b>, toque em <b>+</b> (3) e informe nome, <b>objetivo</b> e prazo.","O anel (1) mostra o progresso: <b>Guardado</b> × <b>Objetivo</b>.","Para guardar dinheiro, toque em <b>Realizar Aporte</b> (2), escolha a conta de origem e o valor.","Para investimentos, toque em <b>Investir</b>: informe <b>Nome</b> (ex.: ITUB4, Bitcoin), <b>Tipo de Ativo</b>, <b>Valor Investido (Custo)</b> e, se quiser, <b>Valor Atual</b>; toque em <b>Guardar</b>."],tela_metas())+
 ""+func("16. Central de Ferramentas","ícone de <b>Ferramentas</b> no cabeçalho","Reúne análises e simuladores. A busca global no topo encontra lançamentos, contas, cartões e metas por texto, valor ou data.",
 ["Toque no ícone de Ferramentas (1).","Digite na busca ou escolha uma ferramenta da lista (2).","Use o botão voltar do Android para retornar ao hub."],tela_hub(),
 "<table class='tb sm'><tr><th>Ferramenta</th><th>Para que serve</th></tr><tr><td>Relatórios</td><td>Filtros combináveis; PDF/PNG/CSV</td></tr><tr><td>Calendário financeiro</td><td>Vencimentos e saldo projetado</td></tr><tr><td>Resumo inteligente</td><td>O que mudou no mês/semana</td></tr><tr><td>Planejamento</td><td>Reserva, 50/30/20, assinaturas</td></tr><tr><td>Regras de categoria</td><td>Categoriza pela descrição</td></tr><tr><td>Projetos · Dívidas</td><td>Reforma/viagem por tags · quitação</td></tr><tr><td>Pessoas e rateios</td><td>Quem te deve; cobrança pronta</td></tr><tr><td>Imposto de Renda</td><td>Triagem da declaração</td></tr><tr><td>Índices, Câmbio, Rendimento, À vista × parcelado, Fluxo de caixa, Reajuste</td><td>Simuladores (dados do Banco Central)</td></tr><tr><td>Lixeira · Conferência · Sugestões · Backups</td><td>Manutenção e segurança</td></tr></table>")+"</section>")

P.append("<section>"+func("17. Captura de notificações e importação de extratos","Configurações","Recursos opcionais para lançar menos manualmente.",
 ["<b>Sugestões por notificação:</b> em Configurações, ative <b>Capturar notificações do banco</b> e conceda o acesso às notificações. O app lê só as notificações dos apps de banco permitidos (você adiciona ou remove pelo nome do pacote).","Pix, compras e saques detectados viram <b>Sugestões de lançamento</b> (Ferramentas): revise e confirme em lote, ou descarte.","<b>Importar extrato (OFX):</b> toque em <b>Escolher arquivos</b>; o app identifica a conta pelo arquivo (confirme ou escolha), mostra o que é novo e você toca em <b>Revisar</b>.","Opcional: <b>Verificar pasta automaticamente</b>, para monitorar uma pasta onde o banco salva extratos."],
 notas=dica("O app ainda não lê CSV nem extrato de cartão de crédito: use a Conciliação do portal nesses casos. As notificações capturadas ficam só no aparelho.","aviso"))+
 func("18. Backups, Lixeira e privacidade","Ferramentas → Backups e histórico / Lixeira","Proteja e recupere seus dados.",
 ["<b>Lixeira:</b> lançamentos excluídos ficam 30 dias. Abra Ferramentas → Lixeira e restaure.","<b>Backups:</b> ative o backup automático (mantém os mais recentes), restaure com segurança, exporte tudo (ZIP) e consulte o histórico de alterações.","<b>Modo privado:</b> oculta os valores na tela (••••••), útil em público.","<b>Widget de saldo:</b> adicione o widget do Meu Dinheiro à tela inicial do Android.","<b>Atalho “Nova despesa”:</b> pressione e segure o ícone do app no launcher."])+"</section>")

P.append("<section class='pg'><h2>Parte 3 — Portal web</h2>"+func("19. Acesso, modos Local e Nuvem","tela de login do portal","O portal roda no navegador. Pode trabalhar com a nuvem (sincronizado com o app) ou somente no próprio navegador.",
 ["Abra o endereço do portal no navegador.","Em <b>Nuvem</b>, entre com o mesmo e-mail e senha do app: os dados aparecem sincronizados.","Em <b>Local</b>, use a opção de modo local: os dados ficam só neste navegador (ideal para testar; em Configurações há <b>Dados de exemplo</b>).","Se aparecer aviso de configuração ausente, o portal só oferece o modo Local; peça ao administrador para configurar o Firebase.","Se você definiu um <b>PIN</b> em Configurações, ele será pedido ao abrir."],
 notas=dica("Os dados do modo Local ficam no navegador: limpar os dados do site apaga tudo. Faça backup em Backups.","aviso"))+
 "<h3>20. Navegação do portal</h3><p>A barra lateral agrupa as páginas por assunto (em telas pequenas ela vira menu). O botão <b>+</b> flutuante, ou a tecla <b>N</b>, abre o lançamento rápido de qualquer página.</p><div class='ilus wide'>"+p_nav()+"</div>"+legenda("Mapa de navegação do portal: grupos e páginas.")+"</section>")

P.append("<section>"+func("21. Início (painel)","página <b>Início</b>","Visão geral do mês: saldo, entradas, saídas, limite diário, gráficos por mês e por categoria, alertas de orçamento e vencimentos.",
 ["Abra <b>Início</b>.","Leia os cartões de totais no topo (1), incluindo o <b>Limite hoje</b>.","Acompanhe o gráfico de receitas × despesas e a divisão por categoria (2).","Veja os alertas (3) e clique para abrir a página correspondente."],"<div class='ilus wide'>"+p_dash()+"</div>").replace("<div class='dois'><div class='ilus'><div class='ilus wide'>","<div class='col'><div class='ilus wide'><div>")+"", )
P[-1]=P[-1]  # (ajustado abaixo)
P.pop()
def func_larga(titulo,onde,texto,steps,ilus,notas=""):
    return f"<section class='func'><h3>{titulo}</h3><p class='onde'>Onde: {onde}</p><p>{texto}</p><div class='ilus wide'>{ilus}</div>{passos(steps)}{notas}</section>"
P.append("<section>"+func_larga("21. Início (painel)","página <b>Início</b>","Visão geral do mês: saldo, entradas, saídas, limite diário, gráficos por mês e por categoria, alertas de orçamento e vencimentos.",
 ["Abra <b>Início</b>.","Leia os cartões de totais no topo (1), incluindo o <b>Limite hoje</b>.","Acompanhe o gráfico de receitas × despesas e a divisão por categoria (2).","Veja os alertas (3) e clique para abrir a página correspondente."],p_dash())+
 func_larga("22. Lançamentos e lançamento rápido","página <b>Lançamentos</b> · botão <b>+</b> · tecla <b>N</b>","Lista, busca e edita todas as movimentações. O lançamento rápido cria um item em segundos.",
 ["Pressione <b>N</b> (fora de campos de texto) ou clique em <b>+ Novo</b> (1).","Opção rápida: em <i>Descreva o lançamento em uma frase</i>, escreva algo como “gastei 45 no mercado ontem” e os campos são preenchidos. Há também <i>Valores recentes</i> (repetir um lançamento) e <i>Lançamento via Pix</i>.","Revise <b>Conta/Cartão</b>, <b>Categoria</b>, <b>Data</b>, <b>Modalidade da compra</b> (crédito/débito), <b>Parcelas</b> e <b>Marcar como pago</b>, e salve.","Na lista, use <b>Buscar</b> e os filtros (2); clique na linha para editar e use o menu da linha (3) para duplicar, dividir ou excluir."],p_lanc())+"</section>")

P.append("<section><h3>23. Contas, Cartões, Pendências e Recorrências</h3><table class='tb'><tr><th style='width:20%'>Página</th><th>O que fazer</th></tr>"
 "<tr><td><b>Contas</b></td><td>Clique em <b>Nova conta</b> e informe banco, conta e saldo inicial. Veja o saldo de cada conta, faça transferências e ajuste o saldo.</td></tr>"
 "<tr><td><b>Cartões</b></td><td>Cadastre cartões (limite, fechamento, vencimento, conta vinculada, modalidade, virtual). Use <b>Nova compra</b> e <b>Pagar fatura</b> (seleciona itens, debita a conta e devolve o limite).</td></tr>"
 "<tr><td><b>Pendências</b></td><td>Contas a pagar e a receber. Dê baixa informando a data do pagamento.</td></tr>"
 "<tr><td><b>Recorrências</b></td><td>Cadastre lançamentos que se repetem (campo “A cada…”); o sistema recupera meses perdidos.</td></tr></table>"+
 func_larga("24. Conciliação de extrato (OFX/CSV)","página <b>Conciliação</b>","Compara o extrato do banco com o que está lançado: marca o que já existe, cria o que falta e atualiza datas. Aceita OFX e CSV (com mapeamento de colunas) e faturas de cartão.",
 ["Abra <b>Conciliação</b> e clique em <b>Escolher arquivos de extrato</b>, ou arraste o arquivo (2).","Para CSV, mapeie <b>Delimitador</b>, <b>Decimal</b> e as colunas de <b>Data</b>, <b>Descrição</b> e <b>Valor</b> (ou crédito/débito); marque <i>A primeira linha é cabeçalho</i>.","Escolha a <b>Conta/cartão</b> de destino. Em fatura de cartão, use <i>Inverter sinal</i> se as compras vierem positivas.","Escolha o <b>Modo de conciliação</b> (3): conciliar automáticos (valor exato, data próxima, candidato único) e/ou criar os que só existem no extrato; ajuste a <b>Janela de datas</b>.","Revise os resultados (Conciliados, Criados, Duplicados ignorados, Pendentes para revisão) e aplique."],p_conc(),
 dica("Fatura fechada e paga? Marque “Marcar como pagos os itens de faturas já quitadas” para quitar tudo de uma vez.","dica"))+"</section>")

P.append("<section><h3>25. Orçamentos, Metas e Planejamento</h3><table class='tb'><tr><th style='width:20%'>Página</th><th>Passo a passo</th></tr>"
 "<tr><td><b>Orçamentos</b></td><td>Defina um teto mensal por categoria. O sistema alerta em 80% e 100% (o número no menu indica quantos alertas existem).</td></tr>"
 "<tr><td><b>Metas</b></td><td>Crie a meta (objetivo e prazo) e faça aportes a partir de uma conta. O sistema calcula o aporte mensal necessário.</td></tr>"
 "<tr><td><b>Planejamento</b></td><td>Reserva de emergência, regra 50/30/20, assinaturas detectadas, melhor dia de compra no cartão e simulador parcelar × à vista.</td></tr>"
 "<tr><td><b>Investimentos</b></td><td>Cadastre ativos (custo e valor atual) e acompanhe a diversificação. <b>Dívidas</b> e <b>Projetos</b> seguem a mesma lógica do app.</td></tr></table>"
 "<h3>26. Ferramentas financeiras</h3><p>Página <b>Ferramentas</b>: painéis para decidir melhor. Preencha os campos e o resultado atualiza na hora.</p><table class='tb'><tr><th style='width:20%'>Painel</th><th>Como usar</th></tr>"
 "<tr><td><b>Câmbio</b></td><td>Escolha a moeda e o valor; veja a conversão R$ ↔ moeda (PTAX do Banco Central) e o gráfico dos últimos fechamentos.</td></tr>"
 "<tr><td><b>Índices</b></td><td>Selic, CDI, IPCA, IGP-M e outros, com anual equivalente.</td></tr>"
 "<tr><td><b>Rendimento</b></td><td>Compare poupança × produto atrelado ao CDI (com IR) × dinheiro parado, descontando a inflação.</td></tr>"
 "<tr><td><b>Parcelado</b></td><td>Informe preço à vista, parcelas e valor da parcela: veja a taxa embutida e se compensa pagar à vista.</td></tr>"
 "<tr><td><b>Dívidas</b></td><td>Custo do rotativo/cheque especial; simule avalanche × bola de neve com valor extra mensal.</td></tr>"
 "<tr><td><b>Fluxo</b></td><td>Projeção de 6 a 12 meses, meses no vermelho e quanto cortar por mês.</td></tr>"
 "<tr><td><b>Reajuste</b></td><td>Novo valor de aluguel/mensalidade por IGP-M, IPCA ou INPC (ou acumulado oficial).</td></tr>"
 "<tr><td><b>Cortes</b></td><td>Sugestões de economia, explicadas.</td></tr></table>"
 "<h3>27. Relatórios e Imposto de Renda</h3>"+passos(["Em <b>Relatórios</b>, escolha um modelo pronto ou monte os filtros (período, conta, cartão físico/virtual, categoria, tipo, pago, texto).","Confira o resultado em tabela e gráficos.","Exporte em <b>PDF</b>, <b>PNG</b> ou <b>CSV</b>.","Em <b>Imposto de Renda</b>, escolha o <b>Ano</b>: o sistema separa rendimentos, despesas dedutíveis, bens e dívidas e exporta PDF/CSV para a triagem."])+
 dica("O Imposto de Renda do sistema é uma <b>triagem</b> para organizar documentos. Confira sempre com um contador ou com o programa oficial da Receita.","aviso")+"</section>")

P.append("<section><h3>28. Categorias, Regras, Pessoas e Membros</h3><table class='tb'><tr><th style='width:20%'>Página</th><th>Passo a passo</th></tr>"
 "<tr><td><b>Categorias</b></td><td>Crie e edite categorias; o sistema sugere a categoria pelo histórico.</td></tr>"
 "<tr><td><b>Regras</b></td><td>Crie regras do tipo “descrição contém uber → Transporte”, teste e aplique às existentes.</td></tr>"
 "<tr><td><b>Pessoas</b></td><td>Controle quem te deve e quem você deve (rateios, empréstimos); gere a cobrança pronta para enviar.</td></tr>"
 "<tr><td><b>Membros</b></td><td>Clique em <b>Convidar por e-mail</b>, informe o e-mail e confirme em <b>Convidar</b>. Depois que a pessoa aceitar, ela acessa a conta compartilhada (modo Nuvem).</td></tr></table>"+
 func("29. Lixeira, Backups e Configurações","grupo <b>Sistema</b>","Segurança e manutenção dos dados.",
 ["<b>Lixeira</b>: itens excluídos ficam 30 dias; restaure o que precisar.","<b>Backups</b>: gere um backup (JSON v2, compatível com o app) e use <b>Restaurar de um arquivo</b> quando necessário. A restauração é tudo-ou-nada.","<b>Configurações</b>: Armazenamento (Local/Nuvem), Aparência e privacidade, PIN, Backup, Dados de exemplo, Conferência de saldos e Zona de perigo.","Notificações push do navegador aparecem em Configurações quando disponíveis."],
 notas=dica("A <b>Zona de perigo</b> apaga todos os dados do modo ativo. Faça um backup antes.","aviso"))+"</section>")

P.append("<section class='pg'><h2>Parte 4 — Apoio</h2><h3>30. Boas práticas e rotina recomendada</h3>"
 "<table class='tb'><tr><th style='width:22%'>Quando</th><th>O que fazer</th></tr>"
 "<tr><td><b>Todo dia</b></td><td>Lance o gasto na hora (frase/voz, cupom) e olhe o “Quanto posso gastar hoje”.</td></tr>"
 "<tr><td><b>Toda semana</b></td><td>Revise as <b>Sugestões</b> de notificações, dê baixa nas <b>Pendências</b> e veja o Resumo inteligente.</td></tr>"
 "<tr><td><b>Todo mês</b></td><td>Importe o extrato no portal (Conciliação), pague a fatura, confira a Conferência de saldos e revise os orçamentos.</td></tr>"
 "<tr><td><b>Todo ano</b></td><td>Use o Imposto de Renda para a triagem e guarde um backup completo (ZIP/JSON).</td></tr></table>"+
 dica("Cadastre sua renda fixa (salário/pró-labore) como <b>recorrência ou pendência a receber</b>. Assim as projeções e o “Quanto posso gastar hoje” ficam realistas sem depender de estimativas.","dica")+
 "<h3>31. Perguntas frequentes e solução de problemas</h3>"
 "<table class='tb'><tr><th style='width:34%'>Problema</th><th>O que fazer</th></tr>"
 "<tr><td>O “Quanto posso gastar hoje” está em zero</td><td>Os compromissos (faturas, contas) superam os saldos e o app não conhece receitas futuras. Cadastre sua renda como recorrência/pendência ou ligue a <b>Renda estimada pelo histórico</b>; toque em <i>Ver como foi calculado</i>.</td></tr>"
 "<tr><td>Compra no débito ficou pendente</td><td>Confirme que o cartão é de <b>débito</b> (ou múltiplo com modalidade débito) e que a data não é futura: compra de débito entra como paga e sai da conta.</td></tr>"
 "<tr><td>Saldo do app diferente do banco</td><td>Use <b>Ajustar saldo da conta</b> ou a <b>Conciliação</b> do portal; rode a <b>Conferência de saldos</b> para recalcular.</td></tr>"
 "<tr><td>Excluí um lançamento sem querer</td><td>Abra a <b>Lixeira</b> (até 30 dias) e restaure.</td></tr>"
 "<tr><td>App e portal mostram dados diferentes</td><td>Verifique se ambos estão em modo <b>Nuvem</b> e no mesmo usuário; toque em <b>Sincronizar agora</b>. No modo Local não há sincronização.</td></tr>"
 "<tr><td>Erro de permissão ao sincronizar (PERMISSION_DENIED)</td><td>As regras do Firestore precisam estar publicadas; avise o administrador do projeto.</td></tr>"
 "<tr><td>O extrato CSV não abre no app</td><td>Use a Conciliação do portal, que lê CSV com mapeamento de colunas.</td></tr>"
 "<tr><td>Não recebo avisos</td><td>Verifique a chave geral de notificações, a permissão do Android e o Motor de alertas em Configurações.</td></tr></table>"
 "<p class='fim'>Meu Dinheiro · Manual do Usuário · Descreve as funcionalidades existentes no projeto na data de geração.</p></section>")

CSS="""
@page { size:A4; margin:16mm 14mm 16mm 14mm; @bottom-center { content: counter(page); font:9pt 'Segoe UI'; color:#667 } }
@page :first { margin:0; @bottom-center { content:'' } }
*{box-sizing:border-box} body{font:10.5pt/1.5 'Segoe UI',Arial,sans-serif;color:#1a2233;margin:0}
h1{font-size:40pt;margin:0;color:#fff} h2{font-size:20pt;color:#0D1B2A;border-bottom:3px solid #00BCD4;padding-bottom:4px;margin:0 0 12px}
h3{font-size:14pt;color:#0D3B5C;margin:16px 0 4px;break-after:avoid} h4{margin:12px 0 2px;color:#00838F}
.capa{height:297mm;background:linear-gradient(160deg,#0D1B2A,#1B3A5C);color:#fff;text-align:center;padding-top:24mm;break-after:page;position:relative}
.capa h2{color:#00E5FF;border:0;font-size:22pt} .capa p{color:#cfd8dc;font-size:12pt}
.logo{font-size:44pt;font-weight:700;color:#00E5FF} .capa-fone{margin-top:10mm} .capa-fone svg{width:190px;height:auto} .ver{position:absolute;bottom:12mm;left:0;right:0;font-size:9pt!important}
.pg{break-before:page}
.func{margin:6px 0 10px;break-inside:avoid} .onde{color:#00695C;font-size:9.5pt;margin:0 0 4px}
.dois{display:flex;gap:14px;align-items:flex-start} .dois>div:last-child{flex:1} .ilus svg{width:178px;height:auto;display:block}
.ilus.wide{margin:6px 0} .ilus.wide svg{width:100%;height:auto}
.steps{padding-left:20px;margin:4px 0} .steps li{margin:3px 0}
.box{border-radius:8px;padding:7px 10px;margin:8px 0;font-size:9.5pt;break-inside:avoid}
.box.dica{background:#E0F7FA;border-left:4px solid #00BCD4} .box.aviso{background:#FFF3E0;border-left:4px solid #FB8C00} .box.nota{background:#ECEFF1;border-left:4px solid #607D8B}
.tb{width:100%;border-collapse:collapse;margin:6px 0;font-size:9.5pt} .tb th{background:#0D3B5C;color:#fff;text-align:left;padding:5px 8px} .tb td{border-bottom:1px solid #d5dde5;padding:5px 8px;vertical-align:top} .tb tr:nth-child(even) td{background:#f5f8fa} .tb.sm{font-size:8.5pt}
.tb tr{break-inside:avoid}
.toc ol{margin:2px 0 6px} .toc li{margin:1px 0} hr{border:0;border-top:1px dashed #b0bec5;margin:14px 0}
.leg{font-size:8.5pt;color:#607D8B;text-align:center;margin:2px 0 10px} .fim{margin-top:24px;text-align:center;color:#78909c;font-size:9pt}
"""
doc=f"<!doctype html><html lang='pt-BR'><head><meta charset='utf-8'><title>Meu Dinheiro — Manual do Usuário</title><style>{CSS}</style></head><body>{''.join(P)}</body></html>"
out=os.path.join(AQUI,"manual.html"); open(out,"w",encoding="utf-8").write(doc)
pdf=os.path.join(AQUI,"Manual_Meu_Dinheiro.pdf")
edge=r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
subprocess.run([edge,"--headless","--disable-gpu","--no-pdf-header-footer",f"--print-to-pdf={pdf}","file:///"+out.replace("\\","/")],check=True,timeout=180)
print("ok",os.path.getsize(pdf))
