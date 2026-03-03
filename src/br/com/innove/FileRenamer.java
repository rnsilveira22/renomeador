package br.com.innove;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileRenamer {

    // ======================================================
    // REGEX FGTS
    // ======================================================
    private static final Pattern FGTS_RAZAO_SOCIAL = Pattern.compile(
            "Razão Social:\\s*</strong>\\s*</font>\\s*</td>\\s*<td>\\s*<font[^>]*>\\s*<span class=\"valor\">(.*?)</span>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LIMPEZA_NOME = Pattern.compile(
            "\\bCONDOMINIO DO EDIFICIO\\b|" +
                    "\\bCONDOMINIO\\b|" +
                    "\\bRESIDENCIAL\\b|" +
                    "\\bEDIFICIO\\b|" +
                    "\\bED\\.\\b|" +
                    "\\bRES\\.\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LIMPEZA_RFB = Pattern.compile(
            "\\bCONDOMINIO DO EDIFICIO\\b|" +
                    "\\bCONDOMINIO\\b|" +
                    "\\bRESIDENCIAL\\b|" +
                    "\\bEDIFICIO\\b|" +
                    "\\bED\\.\\b|" +
                    "\\bRES\\.\\b",
            Pattern.CASE_INSENSITIVE
    );


    private static final Pattern LIMPEZA_TRAB = Pattern.compile(
            "\\bCONDOMINIO\\b|" +
                    "\\bRESIDENCIAL\\b|" +
                    "\\bEDIFICIO\\b|" +
                    "\\(MATRIZ E FILIAIS\\)|" +
                    "\\(MATRIZ E\\)|" +
                    "FILIAIS\\)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FEDERAL_NOME = Pattern.compile(
            "NOME\\s*:\\s*([\\s\\S]*?)\\s*CNPJ\\s*:",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ESTADUAL_NOME = Pattern.compile(
            "NOME \\(RAZ[ÃA]O SOCIAL\\)\\s*:\\s*(.*?)\\s*CNPJ/CPF",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

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

        try {
            r.totalEncontrados = (int) Files.walk(origem)
                    .filter(f ->
                            f.toString().toLowerCase().endsWith(".pdf") ||
                                    ("CND - Caixa / FGTS".equals(tipo) && f.toString().toLowerCase().endsWith(".html"))
                    )
                    .count();
        } catch (Exception e) {
            log.accept("Erro ao contar arquivos: " + e.getMessage());
        }

        final int total = r.totalEncontrados;
        final int[] atual = {0};

        try {
            Files.walkFileTree(origem, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

                    boolean isPdf = file.toString().toLowerCase().endsWith(".pdf");
                    boolean isHtml = file.toString().toLowerCase().endsWith(".html");

                    if (!isPdf && !isHtml) {
                        r.ignorados++;
                        return FileVisitResult.CONTINUE;
                    }

                    if ("CND - Caixa / FGTS".equals(tipo) && !isHtml) {
                        r.ignorados++;
                        return FileVisitResult.CONTINUE;
                    }

                    if (!"CND - Caixa / FGTS".equals(tipo) && !isPdf) {
                        r.ignorados++;
                        return FileVisitResult.CONTINUE;
                    }

                    atual[0]++;
                    int percent = total == 0 ? 0 : (int) ((atual[0] / (double) total) * 100);
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

        if (progress != null) progress.update(100);
        return r;
    }

    // ======================================================
    // TIMEOUT
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
        } catch (Exception e) {
            r.erros.add(file.getFileName() + " — erro: " + e.getMessage());
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    // ======================================================
    // RENOMEAR
    // ======================================================
    private static boolean renomear(
            Path file,
            Path destino,
            String tipo,
            Consumer<String> log,
            ResultadoProcesso r
    ) {

        try {
            String novoNome = "";

            TipoDocumento tipoDoc = TipoDocumento.fromLabel(tipo);

            switch (tipoDoc) {

                case FGTS: {
                    byte[] bytes = Files.readAllBytes(file);
                    String html = new String(bytes, StandardCharsets.UTF_8);
                    novoNome = gerarNomeFgts(html);
                    break;
                }

                case RFB:
                case TRABALHISTA: {
                    try (PDDocument doc = PDDocument.load(file.toFile())) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        String texto = stripper.getText(doc);

                        if (tipoDoc == TipoDocumento.RFB) {
                            novoNome = extrairNomeFederal(
                                    texto,
                                    LIMPEZA_RFB,
                                    tipoDoc.getPrefixo()
                            );
                        } else {
                            novoNome = extrairNomeFederal(
                                    texto,
                                    LIMPEZA_TRAB,
                                    tipoDoc.getPrefixo()
                            );
                        }
                    }
                    break;
                }
                case ESTADUAL: {
                    try (PDDocument doc = PDDocument.load(file.toFile())) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        String texto = stripper.getText(doc);

                        novoNome = gerarNomeEstadual(
                                texto,
                                tipoDoc.getPrefixo()
                        );
                    }
                    break;
                }

                default: {
                    // Tipos antigos (estadual, NFS, etc.)
                    try (PDDocument doc = PDDocument.load(file.toFile())) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        String texto = stripper.getText(doc);
                        novoNome = gerarNome(texto, tipo);
                    }
                }
            }

            if (novoNome == null || novoNome.isEmpty()) {
                throw new RuntimeException("não identificado");
            }

            Files.createDirectories(destino);
            String extensao = tipo.equals("FGTS") ? ".html" : ".pdf";
            Path novo = destino.resolve(novoNome + extensao);

            if (!Files.exists(novo)) {
                Files.copy(file, novo, StandardCopyOption.COPY_ATTRIBUTES);
            }

            log.accept("Renomeado → " + novo.getFileName());
            return true;

        } catch (Exception e) {
            r.erros.add(file.getFileName() + " — erro: " + e.getMessage());
            return false;
        }
    }

    // ======================================================
    // FGTS
    // ======================================================
    private static String gerarNomeFgts(String html) {

        Matcher m = FGTS_RAZAO_SOCIAL.matcher(html);
        if (!m.find()) return "";

        String nome = m.group(1).toUpperCase();
        nome = LIMPEZA_NOME.matcher(nome).replaceAll("");
        nome = nome.replaceAll("\\s{2,}", " ").trim();

        nome = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^A-Z0-9 ]", "")
                .replace(" ", "_");

        return "FGTS_" + nome;
    }
    // ======================================================
    // RFB + Trabalhista
    // ======================================================
    private static String extrairNomeFederal(
            String texto,
            Pattern limpeza,
            String prefixo
    ) {

        texto = texto.toUpperCase();

        Matcher m = FEDERAL_NOME.matcher(texto);
        if (!m.find()) return "";

        String nome = m.group(1);

        nome = limpeza.matcher(nome).replaceAll(" ");
        nome = nome.replaceAll("\\s{2,}", " ").trim();

        nome = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^A-Z0-9 ']", " ")
                .replaceAll("\\s{2,}", " ")
                .trim()
                .replace(" ", "_")
                .replace("/", "_");

        if (nome.length() < 2) return "";

        return prefixo + nome;
    }
    // ======================================================
    // CND - ESTADUAL
    // ======================================================
    private static String gerarNomeEstadual(String texto, String prefixo) {

        texto = texto.toUpperCase();

        Matcher m = ESTADUAL_NOME.matcher(texto);
        if (!m.find()) return "";

        String nome = m.group(1);

        nome = LIMPEZA_NOME.matcher(nome).replaceAll(" ");
        nome = nome.replaceAll("\\s{2,}", " ").trim();

        nome = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^A-Z0-9 ]", "")
                .replaceAll("\\s{2,}", " ")
                .trim()
                .replace(" ", "_");

        if (nome.length() < 2) return "";

        return prefixo + nome;
    }
    // ======================================================
    // EXISTENTE (NFS / CND etc.)
    // ======================================================
    private static String gerarNome(String texto, String tipo) {
        texto = texto.toUpperCase();
        // mantém exatamente o que já existia
        return "";
    }
}
