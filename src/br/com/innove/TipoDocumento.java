package br.com.innove;

public enum TipoDocumento {

    FGTS("CND - Caixa / FGTS", "FGTS_"),
    ESTADUAL("CND - Estadual", "ESTAD_"),
    RFB("CND - Receita Federal / RFB", "RFB_"),
    TRABALHISTA("CND - Trabalhista", "TRAB_"),
    NFS("Nota Fiscal Eletronica", "NFS_");

    private final String label;
    private final String prefixo;

    TipoDocumento(String label, String prefixo) {
        this.label = label;
        this.prefixo = prefixo;
    }

    public String getLabel() {
        return label;
    }

    public String getPrefixo() {
        return prefixo;
    }

    public static TipoDocumento fromLabel(String label) {
        for (TipoDocumento t : values()) {
            if (t.label.equals(label)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo não suportado: " + label);
    }
}
