# Renomeador de Arquivos NFS-e
Aplicação desktop em **Java + Swing** para renomeação automática de Notas Fiscais de Serviço (NFS-e) emitidas por diferentes prefeituras.  
O sistema identifica automaticamente campos essenciais dentro do PDF e gera nomes padronizados, facilitando organização, arquivamento e integração com rotinas contábeis.

---

## Objetivo
Automatizar a leitura e renomeação de arquivos PDF de NFS-e, eliminando processos manuais e reduzindo erros.  
A ferramenta reconhece:

- Prestador do serviço
- Tomador do serviço
- Número da nota
- Data da emissão

A partir disso, gera arquivos com nomes padronizados no formato:

TOMADOR_PRESTADOR_NFS_NUM-123_10-11-2025.pdf

---

## Prefeituras suportadas

### Florianópolis – SC
Reconhece blocos como:  
`EMITENTE DA NFS-e`, `TOMADOR DO SERVIÇO`, `Nome / Nome Empresarial`, entre outros.

### São José – SC
Reconhecimento adaptado para variações do layout.

### Palhoça – SC (ou PDFs escaneados)
PDFs sem texto (somente imagem) são ignorados com aviso:
Ignorado — PDF sem texto (possível imagem escaneada sem OCR)

*(O projeto não utiliza OCR.)*

---

## Funcionalidades principais

- Renomeação automática com detecção inteligente de layout
- Remoção de prefixos do tomador:
    - CONDOMINIO, RESIDENCIAL, EDIFICIO, TORRE, BLOCO, etc.
    - inclusive variações com DO/DA/DOS/DAS
- Não sobrescreve arquivos existentes (contabiliza como sucesso)
- Timeout por arquivo (trava nunca mais)
- Log completo e barra de progresso
- Relatório final:
    - total de arquivos
    - renomeados
    - ignorados
    - erros detalhados
- Interface simples e compatível com Java 8 (Windows/Linux)

---

## Tecnologias utilizadas

| Tecnologia | Finalidade |
|-----------|------------|
| Java 8 | Execução e compatibilidade com clientes Windows |
| Swing | Interface gráfica |
| Apache PDFBox 2.0.35 | Leitura de PDFs |
| ExecutorService | Timeout por arquivo |
| Regex + Normalização Unicode | Limpeza de nomes |

---

## Como executar

### 1. Verifique se o Java 8 está instalado

java -version

Saída esperada:

java version "1.8.0_xxx"

### 2. Execute o JAR

java -jar renomeador.jar
---

## Padrão de nome gerado

Formato final:

TOMADOR_PRESTADOR_NFS_NUM-<número>_<data>.pdf

Exemplo:

CENTRO_EXECUTIVO_ROSAS_VERMELHAS_AZUIS_NFS_NUM-0000_22-11-2025.pdf
---

## Limitações conhecidas

### PDFs sem texto
Não podem ser processados porque o projeto não utiliza OCR.  
Arquivos escaneados são ignorados com aviso claro.

### Somente layouts conhecidos
Novos layouts de prefeituras precisam ser implementados no código.

---

## Empacotando para Windows (.exe ou .msi)

Para gerar `.exe` ou `.msi` é necessário usar o **jpackage**, que só funciona **no Windows para gerar executáveis Windows**.

### 1. Instalar JDK 17 no Windows
Indispensável para usar `jpackage`.

### 2. Gerar o JAR no IntelliJ (Windows)
Use:
Build → Build Artifacts → Build

### 3. Baixar JDK 8 x64 para embutir como runtime
Extraia para algo como:
C:\jdk8-runtime\

### 4. Gerar `.msi`
jpackage ^
--type msi ^
--name RenomeadorNFS ^
--input out\artifacts\renomeador\ ^
--main-jar renomeador.jar ^
--main-class br.com.innove.Main ^
--runtime-image C:\jdk8-runtime ^
--dest dist

### 5. Gerar `.exe`
jpackage ^
--type exe ^
--name RenomeadorNFS ^
--input out\artifacts\renomeador\ ^
--main-jar renomeador.jar ^
--main-class br.com.innove.Main ^
--runtime-image C:\jdk8-runtime ^
--dest dist

### Saída esperada

dist/
├── RenomeadorNFS.exe
└── RenomeadorNFS.msi
---

## Estrutura recomendada do projeto

renomeador/
├─ src/br/com/innove/Main.java
├─ src/br/com/innove/FileRenamer.java
├─ libs/pdfbox-app-2.0.35.jar
├─ README.md
├─ out/artifacts/renomeador/renomeador.jar
└─ dist/ (executáveis gerados)

---

## Licença
Uso interno.
# renomeador
