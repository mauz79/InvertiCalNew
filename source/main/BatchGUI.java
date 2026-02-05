package main;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import dao.FcmDao;
import main.CalendariIncrociati.DatiCompetizione;

public class BatchGUI extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JTabbedPane tabbedPane;
	
	private JTextField txtFcmFile;
	private JTextField txtOutputDir;
	private JComboBox<String> cmbCompetizioni;
	private JComboBox<String> cmbGironi;
	private DefaultListModel<String> listModel;
	private JTextArea logArea;
	private JButton btnEsegui;
	private ArrayList<String[]> executionQueue = new ArrayList<>();
	
	private JComboBox<String> cmbAnalisiComp;
	private JComboBox<String> cmbAnalisiSquadra;
	private JComboBox<String> cmbAnalisiGiornata;
	private JLabel lblPunteggioFanta;
	private JLabel lblMediaGiornata;
	
	private JTable tblDettaglio;
	private DefaultTableModel modelDettaglio;
	private JTable tblClassifica;
	private DefaultTableModel modelClassifica;

	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			try {
				try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
				BatchGUI frame = new BatchGUI();
				frame.setVisible(true);
			} catch (Exception e) { e.printStackTrace(); }
		});
	}

	public BatchGUI() {
		setTitle("InvertiCal - Esecutore & Analisi");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 1000, 750);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);
		
		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		contentPane.add(tabbedPane, BorderLayout.CENTER);
		
		tabbedPane.addTab("1. Esecuzione", null, createBatchPanel(), "Lancio calcoli");
		tabbedPane.addTab("2. Analisi Dettagliata", null, createAnalisiPanel(), "Risultati");
		
		redirectSystemStreams();
	}
	
	private JPanel createBatchPanel() {
		JPanel pnlMain = new JPanel(new BorderLayout(5, 5));
		JPanel pnlTop = new JPanel(new GridLayout(4, 1, 5, 5));
		pnlMain.add(pnlTop, BorderLayout.NORTH);

		// File
		JPanel pnlFile = new JPanel(new BorderLayout(5, 5));
		pnlFile.add(new JLabel("File Lega (.fcm):"), BorderLayout.WEST);
		txtFcmFile = new JTextField();
		pnlFile.add(txtFcmFile, BorderLayout.CENTER);
		JPanel pnlFileBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		JButton btnCarica = new JButton("Carica");
		JButton btnSfogliaFcm = new JButton("Sfoglia...");
		pnlFileBtns.add(btnCarica); pnlFileBtns.add(Box.createHorizontalStrut(5)); pnlFileBtns.add(btnSfogliaFcm);
		pnlFile.add(pnlFileBtns, BorderLayout.EAST);
		pnlTop.add(pnlFile);

		// Output
		JPanel pnlOut = new JPanel(new BorderLayout(5, 5));
		pnlOut.add(new JLabel("Output:"), BorderLayout.WEST);
		txtOutputDir = new JTextField(System.getProperty("user.dir") + File.separator);
		pnlOut.add(txtOutputDir, BorderLayout.CENTER);
		JButton btnSfogliaOut = new JButton("Sfoglia...");
		pnlOut.add(btnSfogliaOut, BorderLayout.EAST);
		pnlTop.add(pnlOut);

		// Selezione
		JPanel pnlSelect = new JPanel(new FlowLayout(FlowLayout.LEFT));
		pnlSelect.setBorder(BorderFactory.createTitledBorder("Selezione"));
		cmbCompetizioni = new JComboBox<>(); cmbCompetizioni.setPreferredSize(new Dimension(250, 25)); cmbCompetizioni.setEnabled(false);
		cmbGironi = new JComboBox<>(); cmbGironi.setPreferredSize(new Dimension(250, 25)); cmbGironi.setEnabled(false);
		pnlSelect.add(new JLabel("Comp:")); pnlSelect.add(cmbCompetizioni);
		pnlSelect.add(new JLabel("Girone:")); pnlSelect.add(cmbGironi);
		pnlTop.add(pnlSelect);
		
		// Coda
		JPanel pnlAdd = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton btnAggiungi = new JButton("Aggiungi alla Coda");
		btnAggiungi.setEnabled(false);
		pnlAdd.add(btnAggiungi);
		pnlTop.add(pnlAdd);

		// Centro
		JPanel pnlCenter = new JPanel(new GridLayout(2, 1, 5, 5));
		pnlMain.add(pnlCenter, BorderLayout.CENTER);
		listModel = new DefaultListModel<>();
		JScrollPane scrollQueue = new JScrollPane(new JList<>(listModel));
		scrollQueue.setBorder(BorderFactory.createTitledBorder("Coda"));
		pnlCenter.add(scrollQueue);
		logArea = new JTextArea(); logArea.setEditable(false); logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		JScrollPane scrollLog = new JScrollPane(logArea);
		scrollLog.setBorder(BorderFactory.createTitledBorder("Log"));
		pnlCenter.add(scrollLog);

		// Sud
		JPanel pnlBot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		btnEsegui = new JButton("AVVIA ELABORAZIONE");
		btnEsegui.setFont(new Font("Tahoma", Font.BOLD, 14));
		btnEsegui.setEnabled(false);
		pnlBot.add(btnEsegui);
		pnlMain.add(pnlBot, BorderLayout.SOUTH);

		// Listeners
		btnSfogliaFcm.addActionListener(e -> {
			JFileChooser jfc = new JFileChooser();
			jfc.setFileFilter(new FileNameExtensionFilter("Database FCM", "fcm"));
			if (jfc.showOpenDialog(BatchGUI.this) == JFileChooser.APPROVE_OPTION) {
				txtFcmFile.setText(jfc.getSelectedFile().getAbsolutePath());
				caricaCompetizioni();
			}
		});
		btnCarica.addActionListener(e -> caricaCompetizioni());
		btnSfogliaOut.addActionListener(e -> {
			JFileChooser jfc = new JFileChooser(); jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (jfc.showOpenDialog(BatchGUI.this) == JFileChooser.APPROVE_OPTION) txtOutputDir.setText(jfc.getSelectedFile().getAbsolutePath() + File.separator);
		});
		cmbCompetizioni.addActionListener(e -> { if (cmbCompetizioni.getSelectedItem() != null) caricaGironi((String) cmbCompetizioni.getSelectedItem()); });
		btnAggiungi.addActionListener(e -> {
			String comp = (String) cmbCompetizioni.getSelectedItem();
			String gir = (String) cmbGironi.getSelectedItem();
			if (comp != null && gir != null) {
				String idComp = comp.split(" - ")[0];
				String nomeComp = comp.substring(comp.indexOf("-")+1).trim();
				if (!listModel.contains("Comp: " + nomeComp + " | " + gir)) {
					listModel.addElement("Comp: " + nomeComp + " | " + gir);
					executionQueue.add(new String[]{idComp, gir});
					btnEsegui.setEnabled(true);
				}
			}
		});
		btnEsegui.addActionListener(e -> eseguiBatch());
		
		return pnlMain;
	}
	
	private JPanel createAnalisiPanel() {
		JPanel pnl = new JPanel(new BorderLayout(5, 5));
		
		// Filtri
		JPanel pnlSel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		pnlSel.setBorder(BorderFactory.createTitledBorder("Filtri"));
		cmbAnalisiComp = new JComboBox<>(); cmbAnalisiComp.setPreferredSize(new Dimension(200, 25));
		cmbAnalisiSquadra = new JComboBox<>(); cmbAnalisiSquadra.setPreferredSize(new Dimension(180, 25));
		cmbAnalisiGiornata = new JComboBox<>(); cmbAnalisiGiornata.setPreferredSize(new Dimension(180, 25));
		
		pnlSel.add(new JLabel("Competizione:")); pnlSel.add(cmbAnalisiComp);
		pnlSel.add(new JLabel("Tua Squadra:")); pnlSel.add(cmbAnalisiSquadra);
		pnlSel.add(new JLabel("Giornata:")); pnlSel.add(cmbAnalisiGiornata);
		pnl.add(pnlSel, BorderLayout.NORTH);
		
		// Tabs interni
		JTabbedPane tabInt = new JTabbedPane();
		
		// 1. Classifica
		String[] colClass = {"Pos", "Squadra", "Punti Avulsa Totali", "Somma Medie (Perf. Index)"};
		modelClassifica = new DefaultTableModel(colClass, 0) { public boolean isCellEditable(int r, int c) { return false; } };
		tblClassifica = new JTable(modelClassifica);
		tblClassifica.setAutoCreateRowSorter(true);
		tblClassifica.setRowHeight(25);
		tabInt.addTab("Classifica Avulsa", new JScrollPane(tblClassifica));
		
		// 2. Dettaglio
		JPanel pnlDett = new JPanel(new BorderLayout());
		
		JPanel pnlInfoDett = new JPanel(new GridLayout(2, 1));
		lblPunteggioFanta = new JLabel("Esegui il calcolo e seleziona i dati.");
		lblPunteggioFanta.setFont(new Font("Tahoma", Font.BOLD, 14));
		lblPunteggioFanta.setForeground(new Color(0, 102, 204));
		pnlInfoDett.add(lblPunteggioFanta);
		
		lblMediaGiornata = new JLabel(" ");
		lblMediaGiornata.setFont(new Font("Tahoma", Font.BOLD, 14));
		lblMediaGiornata.setForeground(new Color(0, 153, 51));
		pnlInfoDett.add(lblMediaGiornata);
		pnlDett.add(pnlInfoDett, BorderLayout.NORTH);
		
		String[] colDett = {"Avversario", "Suo Punteggio (vs Te)", "Risultato"};
		modelDettaglio = new DefaultTableModel(colDett, 0) { public boolean isCellEditable(int r, int c) { return false; } };
		tblDettaglio = new JTable(modelDettaglio);
		tblDettaglio.setRowHeight(25);
		
		DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment(JLabel.CENTER);
		tblDettaglio.getColumnModel().getColumn(1).setCellRenderer(centerRenderer); 
		tblDettaglio.getColumnModel().getColumn(2).setCellRenderer(centerRenderer); 
		
		pnlDett.add(new JScrollPane(tblDettaglio), BorderLayout.CENTER);
		tabInt.addTab("Dettaglio Giornata (Tutti vs Tutti)", pnlDett);
		
		pnl.add(tabInt, BorderLayout.CENTER);
		
		// Eventi
		cmbAnalisiComp.addActionListener(e -> {
			String k = (String) cmbAnalisiComp.getSelectedItem();
			if (k != null) populateAnalisiData(k);
		});
		cmbAnalisiSquadra.addItemListener(e -> updateAnalisiView());
		cmbAnalisiGiornata.addItemListener(e -> updateAnalisiView());
		
		return pnl;
	}

	// --- LOGIC ---
	private void caricaCompetizioni() {
		try {
			try (FcmDao dao = new FcmDao(txtFcmFile.getText())) {
				String[] comps = dao.getListaCompetizioni();
				cmbCompetizioni.removeAllItems();
				for (String s : comps) cmbCompetizioni.addItem(s);
				cmbCompetizioni.setEnabled(true);
			}
		} catch (Throwable ex) {}
	}
	private void caricaGironi(String comp) {
		try {
			try (FcmDao dao = new FcmDao(txtFcmFile.getText())) {
				String[] girs = dao.getListaGironi(comp.split(" - ")[0]);
				cmbGironi.removeAllItems();
				for (String s : girs) cmbGironi.addItem(s);
				cmbGironi.setEnabled(true);
				findButtonByText(this, "Aggiungi").setEnabled(true);
			}
		} catch (Throwable ex) {}
	}
	private void eseguiBatch() {
		final String f = txtFcmFile.getText(); final String o = txtOutputDir.getText();
		btnEsegui.setEnabled(false);
		CalendariIncrociati.archivioRisultati.clear();
		SwingWorker<Void, String> w = new SwingWorker<Void, String>() {
			@Override protected Void doInBackground() throws Exception {
				for (String[] c : executionQueue) {
					publish("Calcolo: " + c[1]);
					try { CalendariIncrociati.doCalcoloLogica(f, o, c[0], c[1]); } catch(Exception e) { publish("Err: "+e); e.printStackTrace(); }
				}
				CalendariIncrociati.generateRiepilogoUnico(o);
				return null;
			}
			@Override protected void process(java.util.List<String> c) { for(String s:c) log(s); }
			@Override protected void done() {
				btnEsegui.setEnabled(true);
				refreshAnalisiTab();
				tabbedPane.setSelectedIndex(1);
				JOptionPane.showMessageDialog(BatchGUI.this, "Calcolo completato!\nFile 'riepilogo_competizioni.html' creato.");
			}
		};
		w.execute();
	}
	
	private void refreshAnalisiTab() {
		cmbAnalisiComp.removeAllItems();
		for (String k : CalendariIncrociati.archivioRisultati.keySet()) cmbAnalisiComp.addItem(k);
	}
	
	private void populateAnalisiData(String key) {
		DatiCompetizione d = CalendariIncrociati.archivioRisultati.get(key);
		if (d == null) return;
		
		cmbAnalisiSquadra.removeItemListener(cmbAnalisiSquadra.getItemListeners()[0]); 
		cmbAnalisiGiornata.removeItemListener(cmbAnalisiGiornata.getItemListeners()[0]); 
		
		cmbAnalisiSquadra.removeAllItems();
		ArrayList<String> sq = new ArrayList<>(d.nomiSquadre.values());
		java.util.Collections.sort(sq);
		for (String s : sq) cmbAnalisiSquadra.addItem(s);
		
		cmbAnalisiGiornata.removeAllItems();
		for (int i=0; i<d.giornate.size(); i++) cmbAnalisiGiornata.addItem("Giornata " + (i+1));
		
		cmbAnalisiSquadra.addItemListener(e -> updateAnalisiView());
		cmbAnalisiGiornata.addItemListener(e -> updateAnalisiView());
		
		// Classifica
		modelClassifica.setRowCount(0);
		Integer[] idxs = new Integer[d.idSquadre.length];
		for(int i=0; i<idxs.length; i++) idxs[i]=i;
		Arrays.sort(idxs, (a,b) -> Integer.compare(d.classificaAvulsaPuntiReali[b], d.classificaAvulsaPuntiReali[a]));
		
		for(int k=0; k<idxs.length; k++) {
			int i = idxs[k];
			double sommaMedie = d.sommaMedieAvulsa[i];
			modelClassifica.addRow(new Object[]{ (k+1), d.nomiSquadre.get(d.idSquadre[i]), d.classificaAvulsaPuntiReali[i], String.format("%.2f", sommaMedie) });
		}
		
		updateAnalisiView();
	}
	
	private void updateAnalisiView() {
		String key = (String) cmbAnalisiComp.getSelectedItem();
		String nomeSq = (String) cmbAnalisiSquadra.getSelectedItem();
		int idxG = cmbAnalisiGiornata.getSelectedIndex();
		
		if (key == null || nomeSq == null || idxG < 0) return;
		
		DatiCompetizione d = CalendariIncrociati.archivioRisultati.get(key);
		int idxSq = -1;
		for (Map.Entry<Integer, String> e : d.nomiSquadre.entrySet()) {
			if (e.getValue().equals(nomeSq)) {
				for(int i=0; i<d.idSquadre.length; i++) if(d.idSquadre[i].equals(e.getKey())) { idxSq=i; break; }
				break;
			}
		}
		
		if (idxSq != -1) {
			double pti = d.mieiPunteggiTotali[idxG][idxSq];
			lblPunteggioFanta.setText("<html>Squadra: <b>"+nomeSq+"</b> | Il tuo Punteggio (Totale): <font color=blue><b>"+String.format("%.2f", pti)+"</b></font></html>");
			
			double mediaG = d.mediePuntiGiornaliere[idxG][idxSq];
			lblMediaGiornata.setText("Media Punti vs Tutti in questa giornata: " + String.format("%.2f", mediaG));
			
			modelDettaglio.setRowCount(0);
			String[][] res = d.risultatiAvulsa.get(idxG);
			double[][] ptiAvv = d.punteggiAvversariControDiMe.get(idxG);
			
			for(int i=0; i<d.idSquadre.length; i++) {
				if (i == idxSq) continue;
				
				String nomeAvversario = d.nomiSquadre.get(d.idSquadre[i]);
				double punteggioAvversarioControDiMe = ptiAvv[idxSq][i];
				String risultato = res[idxSq][i];
				
				modelDettaglio.addRow(new Object[]{ 
					nomeAvversario,
					String.format("%.2f", punteggioAvversarioControDiMe),
					risultato 
				});
			}
		}
	}
	
	// Util
	private JButton findButtonByText(Container c, String t) {
        for (Component x : c.getComponents()) {
            if (x instanceof JButton && ((JButton) x).getText().contains(t)) return (JButton) x;
            if (x instanceof Container) { JButton b = findButtonByText((Container)x, t); if(b!=null) return b; }
        } return null;
    }
	private void log(String m) { logArea.append(m+"\n"); logArea.setCaretPosition(logArea.getDocument().getLength()); }
	private void redirectSystemStreams() {
		Logger.getLogger("main.CalendariIncrociati").addHandler(new Handler() {
			public void publish(LogRecord r) { logArea.append("[LOG] "+r.getMessage()+"\n"); }
			public void flush() {} public void close() {}
		});
	}
}