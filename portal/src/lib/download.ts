/** Dispara o download de um Blob gerado no navegador. */
export function baixarBlob(nome: string, blob: Blob): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = nome;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Dispara o download de um arquivo de texto gerado no navegador. */
export function baixarArquivo(nome: string, conteudo: string, tipo: string): void {
  baixarBlob(nome, new Blob([conteudo], { type: tipo }));
}
