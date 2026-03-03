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

    // ======================================================
    // REGEX NFS - CORRIGIDOS
    // ======================================================
    // Número da NF: aceita "N°.: 29" ou "Nº: 29" ou variações
    private static final Pattern NFS_NUMERO = Pattern.compile(
            "N[°º]\\.?\\s*:\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    // Data de emissão: mais flexível para lidar com quebras de linha
    private static final Pattern NFS_DATA = Pattern.compile(
            "DATA\\s+DE\\s+EMISS[ÃA]O\\s*[\\n\\r\\s]*(\\d{2}/\\d{2}/\\d{4})",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Prestador: captura após RAZÃO SOCIAL, ignorando NOME FANTASIA se presente
    // Pega apenas a primeira linha válida (nome do prestador)
    private static final Pattern NFS_PRESTADOR = Pattern.compile(
            "PRESTADOR\\s+DO\\s+SERVI[ÇC]O\\s+RAZ[ÃA]O\\s+SOCIAL\\s+(?:NOME\\s+FANTASIA\\s+)?([A-Z][A-Z\\s]+?)(?:\\s+\\d|\\s+CNPJ|\\s+NOME\\s+FANTASIA|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Tomador: captura após RAZÃO SOCIAL até CNPJ ou quebra de linha
    private static final Pattern NFS_TOMADOR = Pattern.compile(
            "TOMADOR\\s+DO\\s+SERVI[ÇC]O\\s+RAZ[ÃA]O\\s+SOCIAL\\s+([A-Z][A-Z\\s]+?)(?:\\s+CNPJ|\\s+\\d{2}\\.\\d{3}|\\n|\\r|$)",
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
                case NFS: {
                    try (PDDocument doc = PDDocument.load(file.toFile())) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        String texto = stripper.getText(doc);

                        novoNome = gerarNomeNfs(texto, tipoDoc.getPrefixo());
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


            if (novoNome.isEmpty()) {
                log.accept("Ignorado → nome não identificado");
                return false;
            }

            Files.createDirectories(destino);
            String nomeOriginal = file.getFileName().toString();
            int idx = nomeOriginal.lastIndexOf(".");
            String extensao = idx > 0 ? nomeOriginal.substring(idx) : "";

            Path novo = destino.resolve(novoNome + extensao);

            Files.move(
                    file,
                    novo,
                    StandardCopyOption.REPLACE_EXISTING
            );

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
    // NFS - Nota Fiscal - CORRIGIDO
    // ======================================================

    private static String gerarNomeNfs(String texto, String prefixo) {
        // Normaliza o texto para NFC para resolver problemas de Unicode (Ç vs C+cedilla)
        texto = Normalizer.normalize(texto.toUpperCase(), Normalizer.Form.NFC);

        Matcher mNumero = NFS_NUMERO.matcher(texto);
        Matcher mData = NFS_DATA.matcher(texto);
        Matcher mPrestador = NFS_PRESTADOR.matcher(texto);
        Matcher mTomador = NFS_TOMADOR.matcher(texto);

        // Debug: log para verificar o que foi encontrado
        System.out.println("NFS_NUMERO encontrado: " + mNumero.find());
        System.out.println("NFS_DATA encontrado: " + mData.find());
        System.out.println("NFS_PRESTADOR encontrado: " + mPrestador.find());
        System.out.println("NFS_TOMADOR encontrado: " + mTomador.find());

        // Re-iniciar matchers após find()
        mNumero.reset();
        mData.reset();
        mPrestador.reset();
        mTomador.reset();

        if (!mNumero.find()) {
            System.err.println("NFS: Número não encontrado");
            return "";
        }
        if (!mData.find()) {
            System.err.println("NFS: Data não encontrada");
            return "";
        }
        if (!mPrestador.find()) {
            System.err.println("NFS: Prestador não encontrado");
            return "";
        }
        if (!mTomador.find()) {
            System.err.println("NFS: Tomador não encontrado");
            return "";
        }

        String numero = mNumero.group(1).trim();
        String data = mData.group(1).trim().replace("/", "-");

        String prestadorRaw = mPrestador.group(1).trim();
        String tomadorRaw = mTomador.group(1).trim();

        // Limpar e normalizar nomes
        String prestador = limparNomeNfs(prestadorRaw);
        String tomador = limparNomeNfs(tomadorRaw);

        // Se após limpeza algum nome ficar vazio, usar valor raw truncado
        if (prestador.isEmpty()) {
            prestador = limparNomeNfs(prestadorRaw.replaceAll("[^A-Z\\s]", ""));
        }
        if (tomador.isEmpty()) {
            tomador = limparNomeNfs(tomadorRaw.replaceAll("[^A-Z\\s]", ""));
        }

        // Formato: NFS_TOMADOR_NUMERO_PRESTADOR_DATA
        return prefixo + tomador + "_" + numero + "_" + prestador + "_" + data;
    }

    private static String limparNomeNfs(String nome) {
        if (nome == null || nome.isEmpty()) {
            return "";
        }

        // Remove termos comuns de condomínios
        nome = LIMPEZA_NOME.matcher(nome).replaceAll(" ");

        // Remove números de documento (CPF/CNPJ) que possam ter sido capturados
        nome = nome.replaceAll("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}", "");
        nome = nome.replaceAll("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}", "");
        nome = nome.replaceAll("\\d{11,14}", "");

        // Normaliza espaços
        nome = nome.replaceAll("\\s{2,}", " ").trim();

        // Remove acentos e caracteres especiais
        nome = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");

        // Mantém apenas letras, números e espaços
        nome = nome.replaceAll("[^A-Z0-9 ]", "");

        // Normaliza espaços novamente
        nome = nome.replaceAll("\\s{2,}", " ").trim();

        // Substitui espaços por underscore
        nome = nome.replace(" ", "_");

        return nome;
    }


    private static String gerarNome(String texto, String tipo) {
        texto = texto.toUpperCase();
        // mantém exatamente o que já existia
        return "";
    }
}