package br.com.innove;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.nio.file.*;
import java.util.Arrays;

public class Main {

    private static JTextArea logArea;
    private static JProgressBar barra;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::createGUI);
    }

    private static void createGUI() {

        JFrame frame = new JFrame("Renomeador de Arquivos - Innove Automações");
        URL iconURL = Main.class.getResource("/resources/icon/renomeador.png");
        if (iconURL != null) {
            Image icon = Toolkit.getDefaultToolkit().getImage(iconURL);
            frame.setIconImage(icon);
        }

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLayout(new BorderLayout());

        JPanel painel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblOrigem = new JLabel("Pasta de origem:");
        JTextField txtOrigem = new JTextField(45);
        JButton btnOrigem = new JButton("Selecionar");

        JLabel lblDestino = new JLabel("Pasta de destino:");
        JTextField txtDestino = new JTextField(45);
        JButton btnDestino = new JButton("Selecionar");

        JLabel lblTipo = new JLabel("Tipo:");
        JComboBox<String> comboTipo =new JComboBox<>(Arrays.stream(TipoDocumento.values())
                .map(TipoDocumento::getLabel)
                .toArray(String[]::new));

        gbc.gridx = 0; gbc.gridy = 0; painel.add(lblOrigem, gbc);
        gbc.gridx = 1; painel.add(txtOrigem, gbc);
        gbc.gridx = 2; painel.add(btnOrigem, gbc);

        gbc.gridx = 0; gbc.gridy = 1; painel.add(lblDestino, gbc);
        gbc.gridx = 1; painel.add(txtDestino, gbc);
        gbc.gridx = 2; painel.add(btnDestino, gbc);

        gbc.gridx = 0; gbc.gridy = 2; painel.add(lblTipo, gbc);
        gbc.gridx = 1; painel.add(comboTipo, gbc);

        JButton btnRenomear = new JButton("Iniciar Processo");
        btnRenomear.setPreferredSize(new Dimension(160, 30));

        JButton btnNovoProcesso = new JButton("Novo Processo");
        btnNovoProcesso.setPreferredSize(new Dimension(160, 30));
        btnNovoProcesso.setEnabled(false);


        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        painelBotoes.add(btnRenomear);
        painelBotoes.add(btnNovoProcesso);

        gbc.gridx = 1;
        gbc.gridy = 3;
        painel.add(painelBotoes, gbc);


        frame.add(painel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        frame.add(scroll, BorderLayout.CENTER);

        barra = new JProgressBar(0, 100);
        barra.setStringPainted(true);
        frame.add(barra, BorderLayout.SOUTH);

        btnOrigem.addActionListener(e -> escolherPasta(txtOrigem));
        btnDestino.addActionListener(e -> escolherPasta(txtDestino));

        btnRenomear.addActionListener(e -> {
            btnRenomear.setEnabled(false);
            btnNovoProcesso.setEnabled(false);

            String origem = txtOrigem.getText().trim();
            String destino = txtDestino.getText().trim();
            String tipo = comboTipo.getSelectedItem().toString();

            if (origem.isEmpty() || destino.isEmpty()) {
                log("Informe a pasta de origem e destino.");
                return;
            }

            barra.setValue(0);
            log("\n>>> Iniciando processo...\n");

            FileRenamer.ResultadoProcesso r =
                    FileRenamer.processarDiretorio(
                            Paths.get(origem),
                            Paths.get(destino),
                            tipo,
                            Main::log,
                            percent -> SwingUtilities.invokeLater(() -> barra.setValue(percent))
                    );

            log("\n========== RELATÓRIO FINAL ==========");
            log("Total encontrados: " + r.totalEncontrados);
            log("Renomeados:       " + r.renomeados);
            log("Ignorados:        " + r.ignorados);
            log("Erros:            " + r.erros.size());

            if (!r.erros.isEmpty()) {
                log("\n--- Arquivos ignorados / com erro ---");
                for (String erro : r.erros) {
                    log(" - " + erro);
                }
            }
            btnNovoProcesso.setEnabled(true);

            log("\nProcesso concluído.\n");

        });

        btnNovoProcesso.addActionListener(e -> {

            txtOrigem.setText("");
            txtDestino.setText("");
            comboTipo.setSelectedIndex(0);

            barra.setValue(0);
            logArea.setText("");

            btnRenomear.setEnabled(true);
            btnNovoProcesso.setEnabled(false);

            txtOrigem.requestFocus();
        });


        frame.setVisible(true);
    }

    private static void escolherPasta(JTextField campo) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
            campo.setText(fc.getSelectedFile().getAbsolutePath());
    }

    private static void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
