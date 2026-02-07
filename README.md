# Renomeador de Certidões e Notas Fiscais

Aplicação desktop desenvolvida em **Java 8 + Swing** para **renomeação automática de certidões e documentos fiscais**, a partir da leitura do conteúdo interno dos arquivos (PDF ou HTML).

O sistema foi criado para uso em **escritórios contábeis, administrativos e fiscais**, reduzindo trabalho manual, erros de digitação e tempo gasto na organização de documentos.

---

## 🎯 Objetivo

Automatizar a leitura e renomeação de documentos fiscais e certidões, extraindo o **nome da entidade diretamente do arquivo**, aplicando regras de normalização e gerando nomes padronizados e válidos para sistemas operacionais.

---

## 📄 Tipos de documentos suportados

Atualmente o sistema reconhece e renomeia automaticamente:

- **CND – Caixa / FGTS** (HTML)
- **CND – Receita Federal / RFB** (PDF)
- **CND – Trabalhista (CNDT)** (PDF)
- **Certidões Estaduais** (PDF – conforme padrão existente)
- **Notas Fiscais de Serviço (NFS-e)** (PDF – layouts implementados)

Cada tipo possui regras específicas de leitura, extração e limpeza de nomes.

---

## ⚙️ Funcionamento

1. O usuário seleciona:
  - Tipo do documento
  - Pasta de origem
  - Pasta de destino
2. O sistema:
  - Lê o conteúdo do arquivo
  - Extrai o nome da entidade
  - Remove termos genéricos (ex: CONDOMÍNIO, RESIDENCIAL, EDIFÍCIO)
  - Normaliza caracteres inválidos para nome de arquivo
  - Renomeia e salva o arquivo na pasta de destino
3. Ao final, o usuário pode iniciar **um novo processo sem reiniciar a aplicação**.

---

## 🖥️ Interface

- Interface gráfica simples (Swing)
- Barra de progresso
- Log detalhado por execução
- Relatório final com:
  - total de arquivos
  - renomeados
  - ignorados
  - erros
- Botão **Novo Processo** para processar múltiplas pastas na mesma sessão

---

## 🛠️ Tecnologias utilizadas

| Tecnologia | Finalidade |
|-----------|-----------|
| Java 8 | Compatibilidade com ambientes corporativos |
| Swing | Interface gráfica |
| Apache PDFBox 2.0.35 | Leitura de PDFs |
| Regex + Normalização Unicode | Extração e limpeza de nomes |
| Launch4j | Geração do executável Windows (.exe) |

---

## 📦 Distribuição

O projeto é distribuído como:

- **Executável Windows (.exe)** gerado via Launch4j
- Compatível com **Java 8**
- Ícone personalizado no Windows e na aplicação

---

## ▶️ Execução

### Windows
Basta executar:

---

## ⚠️ Limitações conhecidas

- PDFs sem texto (somente imagem) não são processados  
  *(o projeto não utiliza OCR)*

- Novos layouts de documentos podem exigir ajustes de regex

---

## 📁 Estrutura simplificada do projeto

```text
renomeador/
├── src/
│   ├── br/com/innove/
│   │   ├── Main.java
│   │   ├── FileRenamer.java
│   │   └── TipoDocumento.java
│   └── resources/icon/renomeador.png
├── icon/renomeador.ico
├── renomeador.jar
├── renomeador.xml
└── RenomeadorArquivos.exe
```
---

## 📌 Status do projeto

✔ Estável  
✔ Em uso  
✔ Pronto para ambiente corporativo

---

## 👤 Autor

Rodrigo Norberto da Silveira