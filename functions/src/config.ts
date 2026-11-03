/**
 * Região das funções. Os gatilhos do Firestore precisam ficar na MESMA região (ou multirregião) do banco de dados:
 * confira com `firebase firestore:databases:get "(default)"` e ajuste aqui antes do deploy.
 * Padrão: São Paulo (southamerica-east1).
 */
export const REGIAO = "southamerica-east1";
