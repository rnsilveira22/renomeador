package br.com.innove;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class FileRenamer {

    public interface ProgressCallback {
        void update(int percent);
    }

    public static class ResultadoProcesso {
        public int totalEncontrados = 0;
        public int renomeados = 0;
        public int ignorados = 0;
        public List<String> erros = new ArrayList<>();
    }

    // ======================================================
    // PROCESSO PRINCIPAL
    // ======================================================
    public static ResultadoProcesso processarDiretorio(
            Path origem,
            Path destino,
            String tipo,
            Consumer<String> log,
            ProgressCallback progress
    ) {

        ResultadoProcesso r = new ResultadoProcesso();

        log.accept("====================================================");
        log.accept(">>> INICIANDO PROCESSO DE RENOMEAÇÃO");
        log.accept("Origem:  " + origem.toAbsolutePath());
        log.accept("Destino: " + destino.toAbsolutePath());
        log.accept("====================================================");

        try {
            r.totalEncontrados = (int) Files.walk(origem)
                    .filter(f -> f.toString().toLowerCase().endsWith(".pdf"))
                    .count();
        } catch (Exception e) {
            log.accept("Erro ao contar arquivos: " + e.getMessage());
        }

        log.accept("Total de PDFs encontrados: " + r.totalEncontrados + "\n");

        final int total = r.totalEncontrados;
        final int[] atual = {0};

        try {
            Files.walkFileTree(origem, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

                    if (!file.toString().toLowerCase().endsWith(".pdf")) {
                        r.ignorados++;
                        return FileVisitResult.CONTINUE;
                    }

                    atual[0]++;
                    int percent = total == 0 ? 0 : (int)((atual[0] / (double) total) * 100);

                    log.accept("Lendo (" + atual[0] + "/" + total + "): " + file.getFileName());
                    if (progress != null) progress.update(percent);

                    boolean ok = processarArquivoComTimeout(file, destino, tipo, log, r);

                    if (ok) r.renomeados++;
                    else r.ignorados++;

                    return FileVisitResult.CONTINUE;
                }
            });

        } catch (Exception e) {
            log.accept("Erro geral: " + e.getMessage());
        }

        log.accept("\n====================================================");
        log.accept(">>> PROCESSO FINALIZADO");
        log.accept("Total encontrados: " + r.totalEncontrados);
        log.accept("Renomeados:        " + r.renomeados);
        log.accept("Ignorados:         " + r.ignorados);
        log.accept("Erros:             " + r.erros.size());
        log.accept("====================================================\n");

        if (progress != null) progress.update(100);

        return r;
    }


    // ======================================================
    // TIMEOUT POR ARQUIVO
    // ======================================================
    private static boolean processarArquivoComTimeout(
            Path file, Path destino, String tipo,
            Consumer<String> log, ResultadoProcesso r
    ) {

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<Boolean> future = executor.submit(() ->
                renomear(file, destino, tipo, log, r)
        );

        try {
            return future.get(5, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            log.accept("TIMEOUT → " + file.getFileName());
            r.erros.add(file.getFileName() + " — TIMEOUT");
            future.cancel(true);
            return false;

        } catch (Exception e) {
            log.accept("Erro ao processar " + file.getFileName() + ": " + e.getMessage());
            r.erros.add(file.getFileName() + " — erro inesperado: " + e.getMessage());
            return false;

        } finally {
            executor.shutdownNow();
        }
    }


    // ======================================================
    // RENOMEAR ÚNICO ARQUIVO
    // ======================================================
    private static boolean renomear(
            Path file,
            Path destino,
            String tipo,
            Consumer<String> log,
            ResultadoProcesso r
    ) {

        try (PDDocument doc = PDDocument.load(file.toFile())) {

            PDFTextStripper stripper = new PDFTextStripper();
            String texto = stripper.getText(doc).trim();

            if (texto.isEmpty()) {
                log.accept("Ignorado — PDF sem texto (possível imagem escaneada sem OCR): " + file.getFileName());
                r.erros.add(file.getFileName() + " — PDF sem texto (imagem escaneada)");
                return false;
            }

            String novoNome = gerarNome(texto);

            if (novoNome.isEmpty()) {
                log.accept("Ignorado — não identificado: " + file.getFileName());
                r.erros.add(file.getFileName() + " — não identificado");
                return false;
            }

            Files.createDirectories(destino);
            Path novo = destino.resolve(novoNome + ".pdf");

            if (Files.exists(novo)) {
                log.accept("Já existe (contabilizado como sucesso): " + novo.getFileName());
                return true;
            }

            Files.copy(file, novo, StandardCopyOption.COPY_ATTRIBUTES);

            log.accept("Renomeado → " + novo.getFileName());
            return true;

        } catch (Exception e) {
            r.erros.add(file.getFileName() + " — erro ao abrir/copiar: " + e.getMessage());
            return false;
        }
    }


    // ======================================================
    // IDENTIFICA LAYOUT E EXTRAI
    // ======================================================
    private static String gerarNome(String texto) {

        texto = texto.toUpperCase();

        int countNome = contarOcorrencias(texto, "NOME / NOME EMPRESARIAL");

        // tanto Florianópolis quanto São José seguem esse padrão
        if (countNome >= 2)
            return gerarNomeGenerico(texto);

        return "";
    }

    private static int contarOcorrencias(String texto, String termo) {
        int count = 0;
        for (String linha : texto.split("\\r?\\n")) {
            if (linha.contains(termo)) count++;
        }
        return count;
    }


    // ======================================================
    // EXTRAÇÃO UNIFICADA (FLORIANÓPOLIS + SÃO JOSÉ)
    // ======================================================
    private static String gerarNomeGenerico(String texto) {

        String prestador = extrairOcorrencia(texto, "NOME / NOME EMPRESARIAL", 1);
        String tomador   = extrairOcorrencia(texto, "NOME / NOME EMPRESARIAL", 2);
        String numero    = extrairNumero(texto);
        String data      = extrairData(texto);

        if (prestador == null || tomador == null || numero == null || data == null)
            return "";

        // limpeza
        prestador = limparNome(prestador);
        tomador   = limparNome(tomador);

        // **AQUI está a melhoria: limpeza robusta do tomador**
        tomador   = limparTomadorRobusto(tomador);

        return tomador + "_" + prestador + "_NFS_NUM-" + numero + "_" + data;
    }


    // extrai ocorrência n
    private static String extrairOcorrencia(String texto, String marcador, int n) {
        String[] linhas = texto.split("\\r?\\n");
        marcador = marcador.toUpperCase();
        int count = 0;

        for (int i = 0; i < linhas.length - 1; i++) {
            if (linhas[i].contains(marcador)) {
                count++;
                if (count == n)
                    return linhas[i + 1].trim();
            }
        }
        return null;
    }

    private static String extrairNumero(String texto) {
        String[] linhas = texto.split("\\r?\\n");
        for (int i = 0; i < linhas.length - 1; i++) {
            String up = linhas[i].toUpperCase();
            if (up.contains("NÚMERO DA NFS-E") || up.contains("NUMERO DA NFS-E") || up.matches(".*N[ºº].*NFS[- ]?E.*"))
                return linhas[i + 1].trim();
        }
        return null;
    }

    private static String extrairData(String texto) {
        String[] linhas = texto.split("\\r?\\n");
        for (int i = 0; i < linhas.length - 1; i++) {
            String up = linhas[i].toUpperCase();
            if (up.contains("DATA E HORA DA EMISS") || up.contains("DATA/HORA DA EMISS")) {
                return linhas[i + 1].split(" ")[0].replace("/", "-");
            }
        }
        return null;
    }


    // ======================================================
    // TRATAMENTO DE TEXTO E LIMPEZA ROBUSTA
    // ======================================================
    private static String limparNome(String s) {
        if (s == null) return null;
        s = s.trim();
        s = removerDocumentos(s);
        s = removerAcentos(s);
        return sanitize(s);
    }

    private static String removerDocumentos(String s) {
        // remove CPF/CNPJ quando estiver no começo da linha
        return s.replaceAll("^\\d[\\d./-]*\\s+", "").trim();
    }

    private static String removerAcentos(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n;
    }

    /**
     * Limpeza robusta do tomador:
     * - Remove sequências de prefixos conhecidos no início (ex.: "CONDOMINIO DO EDIFICIO RESIDENCIAL ...")
     * - Trata "DO/DA/DOS/DAS" entre prefixos
     * - Repete até não haver mais prefixo no início
     */
    // java
    private static String limparTomadorRobusto(String s) {
        if (s == null) return null;

        // normaliza underscores (vêm de sanitize) para espaços para a regex funcionar
        String original = s.trim();
        String withSpaces = original.replaceAll("_+", " ").trim();

        // remove acentos e coloca em maiúsculo para comparação
        String normalized = removerAcentos(withSpaces).toUpperCase().trim();

        String[] prefArray = {
                "CONDOMINIO", "RESIDENCIAL", "EDIFICIO", "EDIF", "EDF", "ED", "PREDIO",
                "TORRE", "BLOCO", "CENTRO", "COMERCIAL", "EXECUTIVO", "MULTIFAMILIAR"
        };
        String prefixos = String.join("|", prefArray);

        String regexStart = "^(?:(?:" + prefixos + "))(?:\\b\\s+(?:DO|DA|DOS|DAS))?\\b\\s*";

        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regexStart);
        java.util.regex.Matcher m = p.matcher(normalized);

        while (m.find() && m.start() == 0) {
            normalized = normalized.substring(m.end()).trim();
            m = p.matcher(normalized);
        }

        normalized = normalized.replaceFirst("^(?:(?:DO|DA|DOS|DAS)\\b\\s*)+", "").trim();
        normalized = normalized.replaceAll("\\s+", " ");

        // volta a sanitizar (underscore) antes de retornar
        return sanitize(normalized);
    }



    private static String sanitize(String s) {
        if (s == null) return null;
        // remove caracteres não alfanuméricos e converte espaços para underscore
        return s.replaceAll("[^A-Z0-9]+", "_");
    }
}
