package com.meudinheiro.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Catálogo de ícones de categoria (R50). As chaves `pic` são as MESMAS do portal
 * (`portal/src/components/ui/PicIcon.tsx`, `GRUPOS_PICS`). Ordem e rótulos idênticos.
 * Resolução no app: drawable existente (getIdentifier) -> este catálogo -> fallback.
 */
data class IconeCategoria(val chave: String, val rotulo: String, val vetor: ImageVector)

data class GrupoIcones(val rotulo: String, val itens: List<IconeCategoria>)

object IconesCategoria {
    /** Chaves originais (drawables) que NUNCA devem ser sobrescritas por este catálogo. */
    val CHAVES_ORIGINAIS = listOf(
        "fuel", "restaurant", "transport", "shopping", "cinema", "health", "education",
        "salary", "repair_car", "supermarket", "gym", "games", "drink", "lunch", "reserva"
    )

    val GRUPOS: List<GrupoIcones> by lazy {
        listOf(
            GrupoIcones("Alimentação", listOf(
                IconeCategoria("mercado", "Mercado", Icons.Rounded.ShoppingBasket),
                IconeCategoria("padaria", "Padaria", Icons.Rounded.BakeryDining),
                IconeCategoria("cafe", "Café", Icons.Rounded.Coffee),
                IconeCategoria("pizza", "Pizza", Icons.Rounded.LocalPizza),
                IconeCategoria("delivery", "Delivery", Icons.Rounded.DeliveryDining),
                IconeCategoria("bar", "Bar", Icons.Rounded.LocalBar),
                IconeCategoria("sorvete", "Sorvete", Icons.Rounded.Icecream),
                IconeCategoria("fastfood", "Fast food", Icons.Rounded.Fastfood),
                IconeCategoria("acougue", "Açougue", Icons.Rounded.SetMeal),
                IconeCategoria("hortifruti", "Hortifruti", Icons.Rounded.Eco)
            )),
            GrupoIcones("Casa", listOf(
                IconeCategoria("aluguel", "Aluguel", Icons.Rounded.Home),
                IconeCategoria("condominio", "Condomínio", Icons.Rounded.Apartment),
                IconeCategoria("luz", "Luz", Icons.Rounded.Lightbulb),
                IconeCategoria("agua", "Água", Icons.Rounded.WaterDrop),
                IconeCategoria("gas", "Gás", Icons.Rounded.LocalFireDepartment),
                IconeCategoria("internet", "Internet", Icons.Rounded.Wifi),
                IconeCategoria("telefone", "Telefone", Icons.Rounded.Phone),
                IconeCategoria("moveis", "Móveis", Icons.Rounded.Chair),
                IconeCategoria("reforma", "Reforma", Icons.Rounded.Construction),
                IconeCategoria("ferramentas", "Ferramentas", Icons.Rounded.Handyman),
                IconeCategoria("jardim", "Jardim", Icons.Rounded.Yard),
                IconeCategoria("limpeza", "Limpeza", Icons.Rounded.CleaningServices),
                IconeCategoria("eletrodomesticos", "Eletrodomésticos", Icons.Rounded.Kitchen)
            )),
            GrupoIcones("Transporte", listOf(
                IconeCategoria("onibus", "Ônibus", Icons.Rounded.DirectionsBus),
                IconeCategoria("taxi", "Táxi", Icons.Rounded.LocalTaxi),
                IconeCategoria("uber", "App de transporte", Icons.Rounded.Navigation),
                IconeCategoria("estacionamento", "Estacionamento", Icons.Rounded.LocalParking),
                IconeCategoria("pedagio", "Pedágio", Icons.Rounded.Toll),
                IconeCategoria("manutencao_carro", "Manutenção do carro", Icons.Rounded.CarRepair),
                IconeCategoria("bicicleta", "Bicicleta", Icons.Rounded.PedalBike),
                IconeCategoria("metro", "Metrô e trem", Icons.Rounded.Train)
            )),
            GrupoIcones("Saúde e bem-estar", listOf(
                IconeCategoria("farmacia", "Farmácia", Icons.Rounded.LocalPharmacy),
                IconeCategoria("dentista", "Dentista", Icons.Rounded.Mood),
                IconeCategoria("exercicio", "Exercício", Icons.Rounded.SelfImprovement),
                IconeCategoria("beleza", "Beleza", Icons.Rounded.Face),
                IconeCategoria("cabelo", "Cabelo", Icons.Rounded.ContentCut),
                IconeCategoria("terapia", "Terapia", Icons.Rounded.Psychology),
                IconeCategoria("consulta", "Consulta médica", Icons.Rounded.MedicalServices),
                IconeCategoria("oculos", "Óculos", Icons.Rounded.Visibility)
            )),
            GrupoIcones("Educação", listOf(
                IconeCategoria("escola", "Escola", Icons.Rounded.School),
                IconeCategoria("livros", "Livros", Icons.Rounded.MenuBook),
                IconeCategoria("cursos", "Cursos", Icons.Rounded.CastForEducation),
                IconeCategoria("material_escolar", "Material escolar", Icons.Rounded.Edit)
            )),
            GrupoIcones("Lazer", listOf(
                IconeCategoria("festa", "Festa", Icons.Rounded.Celebration),
                IconeCategoria("musica", "Música", Icons.Rounded.MusicNote),
                IconeCategoria("esporte", "Esporte", Icons.Rounded.EmojiEvents),
                IconeCategoria("camera", "Fotografia", Icons.Rounded.PhotoCamera),
                IconeCategoria("streaming", "Streaming", Icons.Rounded.Tv),
                IconeCategoria("shows", "Shows e eventos", Icons.Rounded.ConfirmationNumber),
                IconeCategoria("parque", "Parques e passeios", Icons.Rounded.Park)
            )),
            GrupoIcones("Compras e vestuário", listOf(
                IconeCategoria("roupas", "Roupas", Icons.Rounded.Checkroom),
                IconeCategoria("calcados", "Calçados", Icons.Rounded.DirectionsWalk),
                IconeCategoria("eletronicos", "Eletrônicos", Icons.Rounded.Laptop),
                IconeCategoria("acessorios", "Acessórios", Icons.Rounded.Watch),
                IconeCategoria("compras_online", "Compras online", Icons.Rounded.Language)
            )),
            GrupoIcones("Família e pets", listOf(
                IconeCategoria("pet", "Pet", Icons.Rounded.Pets),
                IconeCategoria("veterinario", "Veterinário", Icons.Rounded.Vaccines),
                IconeCategoria("crianca", "Criança", Icons.Rounded.ChildCare),
                IconeCategoria("familia", "Família", Icons.Rounded.FamilyRestroom),
                IconeCategoria("brinquedos", "Brinquedos", Icons.Rounded.Extension)
            )),
            GrupoIcones("Trabalho e finanças", listOf(
                IconeCategoria("freelance", "Freelance", Icons.Rounded.Work),
                IconeCategoria("bonus", "Bônus", Icons.Rounded.Stars),
                IconeCategoria("investimento", "Investimento", Icons.Rounded.TrendingUp),
                IconeCategoria("emprestimo", "Empréstimo", Icons.Rounded.RequestQuote),
                IconeCategoria("juros", "Juros", Icons.Rounded.Percent),
                IconeCategoria("renda_extra", "Renda extra", Icons.Rounded.AttachMoney),
                IconeCategoria("escritorio", "Escritório", Icons.Rounded.Business)
            )),
            GrupoIcones("Serviços e assinaturas", listOf(
                IconeCategoria("assinatura", "Assinatura", Icons.Rounded.Subscriptions),
                IconeCategoria("lavanderia", "Lavanderia", Icons.Rounded.LocalLaundryService),
                IconeCategoria("correios", "Correios", Icons.Rounded.LocalPostOffice),
                IconeCategoria("cartorio", "Cartório", Icons.Rounded.HistoryEdu),
                IconeCategoria("advogado", "Advogado", Icons.Rounded.Gavel),
                IconeCategoria("contador", "Contador", Icons.Rounded.Calculate),
                IconeCategoria("seguro", "Seguro", Icons.Rounded.Security),
                IconeCategoria("nuvem", "Nuvem e armazenamento", Icons.Rounded.Cloud),
                IconeCategoria("aplicativos", "Aplicativos", Icons.Rounded.Apps)
            )),
            GrupoIcones("Viagens", listOf(
                IconeCategoria("viagem", "Viagem", Icons.Rounded.Luggage),
                IconeCategoria("hotel", "Hotel", Icons.Rounded.Hotel),
                IconeCategoria("aviao", "Avião", Icons.Rounded.Flight),
                IconeCategoria("praia", "Praia", Icons.Rounded.BeachAccess),
                IconeCategoria("passeios", "Mapa e passeios", Icons.Rounded.Map),
                IconeCategoria("camping", "Camping", Icons.Rounded.Terrain)
            )),
            GrupoIcones("Presentes e doações", listOf(
                IconeCategoria("presente", "Presente", Icons.Rounded.CardGiftcard),
                IconeCategoria("doacao", "Doação", Icons.Rounded.VolunteerActivism),
                IconeCategoria("igreja", "Igreja", Icons.Rounded.Church),
                IconeCategoria("flores", "Flores", Icons.Rounded.LocalFlorist),
                IconeCategoria("aniversario", "Aniversário", Icons.Rounded.Cake)
            )),
            GrupoIcones("Impostos e taxas", listOf(
                IconeCategoria("imposto", "Imposto", Icons.Rounded.Description),
                IconeCategoria("ipva", "IPVA", Icons.Rounded.DirectionsCar),
                IconeCategoria("iptu", "IPTU", Icons.Rounded.HomeWork),
                IconeCategoria("taxas_bancarias", "Taxas bancárias", Icons.Rounded.ReceiptLong),
                IconeCategoria("multa", "Multas", Icons.Rounded.ReportProblem),
                IconeCategoria("irpf", "IRPF", Icons.Rounded.FactCheck)
            )),
            GrupoIcones("Outros", listOf(
                IconeCategoria("outros", "Outros", Icons.Rounded.Category),
                IconeCategoria("favorito", "Favorito", Icons.Rounded.Favorite),
                IconeCategoria("imprevisto", "Imprevistos", Icons.Rounded.Bolt)
            )),
        )
    }

    val MAPA: Map<String, IconeCategoria> by lazy { GRUPOS.flatMap { it.itens }.associateBy { it.chave } }

    /** Ícone do catálogo para a chave, ou null se desconhecida (o chamador aplica o fallback). */
    fun porChave(pic: String?): IconeCategoria? = pic?.trim()?.lowercase()?.let { MAPA[it] }
}
