package main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import model.Incontro;
import model.Match;
import model.Tabellino;
import model.Fascia;
import dao.FcmDao;
import exception.InvalidGiornataException;
import fcm.CalcoliHelper;

public class CalendariIncrociati {

	private static String filename = "";
	private static Logger logger = Logger.getLogger("main.CalendariIncrociati");
	
	// --- MEMORIA DATI ---
	public static Map<String, DatiCompetizione> archivioRisultati = new HashMap<>();
	
	public static class DatiCompetizione {
		public String nomeCompetizione;
		public String nomeGirone;
		public Integer[] idSquadre;
		public Hashtable<Integer, String> nomiSquadre;
		public ArrayList<Integer> giornate;
		
		public ArrayList<String[][]> risultatiAvulsa = new ArrayList<>();
		public ArrayList<double[][]> punteggiAvversariControDiMe = new ArrayList<>();
		public double[][] mieiPunteggiTotali; 
		public double[][] mediePuntiGiornaliere; 
		
		public int[] classificaAvulsaPuntiReali; 
		public double[] sommaMedieAvulsa; 
		
		public int[][] matricePuntiSimulazioneCalendari; 
		public int[] puntiRealiCampionato; 
		
		public String pathOutput;
	}

	static {
		try {
			logger = Logger.getLogger("main.CalendariIncrociati");
			logger.setLevel(Level.ALL);
			FileHandler fh = new FileHandler("log.txt");
			logger.addHandler(fh);
			SimpleFormatter formatter = new SimpleFormatter();
			fh.setFormatter(formatter);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String args[]){
		if (args.length==0){
			BatchGUI.main(args);
		} else if (args.length==3){
			BatchGUI.main(args); 
		}
	}

	public static void doCalcoloLogica(String dbFilename, String outputRoot, String idCompetizione, String optionGirone) throws Exception {
		CalendariIncrociati.filename = dbFilename;

		try (FcmDao dao = new FcmDao(dbFilename)){
			
			String idGir = optionGirone.split(" - ")[0];
			String nomeGirone = optionGirone.split(" - ").length > 1 ? optionGirone.split(" - ")[1] : "Girone_" + idGir;
			String nomeComp = dao.getNomeCompetizione(idCompetizione); 
			
			String folderName = idCompetizione + "_" + nomeGirone.replaceAll("[^a-zA-Z0-9.-]", "_");
			String targetDir = outputRoot + "incrodet" + File.separator + folderName + File.separator;
			
			File dir = new File(targetDir);
			if (!dir.exists()) dir.mkdirs(); 
			
			DatiCompetizione dati = new DatiCompetizione();
			dati.nomeCompetizione = nomeComp;
			dati.nomeGirone = nomeGirone;
			dati.pathOutput = folderName; 

			Hashtable<Integer,String> nomiteam = dao.getSquadreGirone(idGir);
			Regole r = dao.getRegoleCompetizione(idCompetizione);
			ArrayList<Integer> giornate = dao.getGiornate(idGir);
			Integer[] squadreArray = dao.getSquadreIscritte(idGir);
			int numSquadre = squadreArray.length;
			
			dati.idSquadre = squadreArray;
			dati.nomiSquadre = nomiteam;
			dati.giornate = giornate;
			dati.mieiPunteggiTotali = new double[giornate.size()][numSquadre];
			dati.mediePuntiGiornaliere = new double[giornate.size()][numSquadre];
			dati.matricePuntiSimulazioneCalendari = new int[numSquadre][numSquadre];
			dati.puntiRealiCampionato = new int[numSquadre];
			
			List<Fascia> fasceModDifesa = dao.getFasceModificatoreDifesa(idCompetizione);
			List<Fascia> fasceNumeroDifensori = dao.getContributoNumeroDifensoriModificatoreDifesa(idCompetizione);
			List<Fascia> fasceModCentrocampo = dao.getFasceModificatoreCentrocampo(idCompetizione);
			List<Fascia> fasceGol = dao.getFasceConversioneGol(idCompetizione);
			
			CalcoliHelper calcoliHelper = new CalcoliHelper(r, fasceModDifesa, fasceNumeroDifensori, fasceModCentrocampo, fasceGol);

			int[] classificaAvulsaTotale = new int[numSquadre]; 
			double[] sommaMedieTotale = new double[numSquadre];
			
			// --- CICLO GIORNATE ---
			for (int i=0; i<giornate.size(); i++){
				int idGiornata = giornate.get(i);
				
				// UGO: Sostituito FileWriter con OutputStreamWriter per UTF-8 anche qui
				BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetDir + idGiornata + ".txt"), StandardCharsets.UTF_8));
				
				Map<Integer, Integer> calendarioRealeMap = new HashMap<>();
				Map<Integer, Boolean> casaRealeMap = new HashMap<>(); 
				
				ArrayList<String> idIncontriFiltro = new ArrayList<>();
				boolean esisteFattoreCampo = false;
				
				List<Incontro> incontri = dao.getIncontri(idGir, idGiornata);
				for (Incontro inc: incontri){
					if (inc.fattoreCampo) esisteFattoreCampo=true;
					idIncontriFiltro.add(inc.idIncontro);
					int idCasa = Integer.parseInt(inc.casa);
					int idTrasf = Integer.parseInt(inc.trasferta);
					calendarioRealeMap.put(idCasa, idTrasf);
					calendarioRealeMap.put(idTrasf, idCasa);
					casaRealeMap.put(idCasa, true);
					casaRealeMap.put(idTrasf, false);
				}

				Map<Integer, Tabellino> tabellini = null;
				try {
					tabellini = dao.getTabellini(idIncontriFiltro);
				} catch (InvalidGiornataException e) {
					w.close();
					dati.risultatiAvulsa.add(new String[numSquadre][numSquadre]);
					dati.punteggiAvversariControDiMe.add(new double[numSquadre][numSquadre]);
					continue;
				}

				// --- 1. SIMULAZIONE ---
				String[][] risPerRender = new String[numSquadre][numSquadre];
				for(int j=0; j<numSquadre; j++){
					Integer idSquadraJ = squadreArray[j];
					Tabellino tabJ = tabellini.get(idSquadraJ);
					
					for (int k=0; k<numSquadre; k++){
						Integer idProprietarioCalendario = squadreArray[k];
						Integer idAvversarioDiK = calendarioRealeMap.get(idProprietarioCalendario);
						
						if (idAvversarioDiK != null) {
							Integer idAvversarioSimulato = idAvversarioDiK;
							boolean giocaInCasa = casaRealeMap.get(idProprietarioCalendario);
							
							if (idAvversarioSimulato.equals(idSquadraJ)) {
								giocaInCasa = !giocaInCasa; 
								idAvversarioSimulato = idProprietarioCalendario; 
							}
							
							Tabellino tabAvv = tabellini.get(idAvversarioSimulato);
							Match m = calcoliHelper.calcolaMatch(tabJ, tabAvv, giocaInCasa, esisteFattoreCampo);
							
							int pti = 0;
							if (m.squadra1.numeroGol > m.squadra2.numeroGol) pti = r.puntiPerVittoria;
							else if (m.squadra1.numeroGol == m.squadra2.numeroGol) pti = 1;
							
							dati.matricePuntiSimulazioneCalendari[j][k] += pti;
							
							if (j == k) {
								dati.puntiRealiCampionato[j] += pti;
							}
							
							// LOGICA HTML SINGOLA GIORNATA
							String g = m.squadra1.numeroGol + "-"+ m.squadra2.numeroGol;
							String nomeAvv = nomiteam.get(idAvversarioSimulato);
							risPerRender[j][k] = calcolaRisPerRender(r, nomiteam.get(idSquadraJ), nomeAvv, giocaInCasa, m);
							risPerRender[j][k] += pti+ "("+g+")   ";
							w.append(risPerRender[j][k]);
						}
					}
					w.newLine();
				}
				
				// --- 2. AVULSA ---
				String[][] avulsaRisultati = new String[numSquadre][numSquadre];
				double[][] avulsaPunteggiAvversari = new double[numSquadre][numSquadre];
				int[] puntiGiornataAvulsa = new int[numSquadre];
				
				for (int j=0; j < numSquadre; j++) {
					Tabellino tabA = tabellini.get(squadreArray[j]);
					Match matchBase = calcoliHelper.calcolaMatch(tabA, tabA, true, false);
					dati.mieiPunteggiTotali[i][j] = matchBase.squadra1.getTotale();

					for (int k=0; k < numSquadre; k++) {
						if (j == k) continue; 
						Tabellino tabB = tabellini.get(squadreArray[k]);
						Match matchAvulsa = calcoliHelper.calcolaMatch(tabA, tabB, true, false);
						avulsaRisultati[j][k] = matchAvulsa.squadra1.numeroGol + "-" + matchAvulsa.squadra2.numeroGol;
						avulsaPunteggiAvversari[j][k] = matchAvulsa.squadra2.getTotale();
						
						int ptiAvulsa = 0;
						if (matchAvulsa.squadra1.numeroGol > matchAvulsa.squadra2.numeroGol) ptiAvulsa = r.puntiPerVittoria;
						else if (matchAvulsa.squadra1.numeroGol == matchAvulsa.squadra2.numeroGol) ptiAvulsa = 1;
						
						classificaAvulsaTotale[j] += ptiAvulsa;
						puntiGiornataAvulsa[j] += ptiAvulsa;
					}
					double mediaG = (double)puntiGiornataAvulsa[j] / (numSquadre - 1);
					dati.mediePuntiGiornaliere[i][j] = mediaG;
					sommaMedieTotale[j] += mediaG;
				}
				dati.risultatiAvulsa.add(avulsaRisultati);
				dati.punteggiAvversariControDiMe.add(avulsaPunteggiAvversari);
				
				w.flush(); w.close();
				renderHTML(risPerRender, targetDir + idGiornata + ".html", squadreArray, nomiteam);
			}

			dati.classificaAvulsaPuntiReali = classificaAvulsaTotale;
			dati.sommaMedieAvulsa = sommaMedieTotale; 
			archivioRisultati.put(idCompetizione + "_" + optionGirone, dati);

			// File totali (vecchio stile)
			// UGO: Sostituito FileWriter
			BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetDir + "totale.txt"), StandardCharsets.UTF_8));
			for(int j=1; j<=squadreArray.length; j++){
				for (int k=1; k<=squadreArray.length; k++){
					w.append("0    "); // Placeholder
				}
				w.newLine();
			}
			w.flush(); w.close();

			renderClassificaAvulsaHTML(classificaAvulsaTotale, sommaMedieTotale, giornate, targetDir + "classifica_avulsa.html", squadreArray, nomiteam);
			generateRiepilogoUnico(outputRoot);
		}
	}
	
	// --- GENERATORE HTML ---
	public static void generateRiepilogoUnico(String outputRoot) {
		try {
			File file = new File(outputRoot + "riepilogo_competizioni.html");
			BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
			
			createDefaultCssFile(outputRoot);
			
			w.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">");
			w.append("<html><head><title>Calendario Incrociato</title>");
			w.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
			w.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"incroci.css\">");
			w.append("</head><body>");
			
			List<DatiCompetizione> listaDati = new ArrayList<>(archivioRisultati.values());
			Collections.sort(listaDati, (o1, o2) -> o1.nomeCompetizione.compareTo(o2.nomeCompetizione));
			
			for (DatiCompetizione d : listaDati) {
				w.append("<br><div class='container_competizione'>");
				w.append("<h2 class=\"titletext\">" + d.nomeCompetizione + " - " + d.nomeGirone + "</h2><br>");
				
				w.append("<table width='100%' border='0' cellspacing='5'><tr>");
				
				// MATRICE
				w.append("<td valign='top' width='68%'>");
				w.append("<div class='rigaintestazione'>Se avessi avuto il calendario di...</div>");
				w.append("<table class='ClassTabellaincroci' width='100%'>");
				
				w.append("<tr class='rigasquadre'><td>&nbsp;</td>");
				for (int k=0; k<d.idSquadre.length; k++) {
					String nomeSq = d.nomiSquadre.get(d.idSquadre[k]);
					w.append("<td class='header-squadra'>"+nomeSq+"</td>");
				}
				w.append("</tr>");
				
				for (int j=0; j<d.idSquadre.length; j++) {
					String rowClass = (j % 2 == 0) ? "rigaincrocipari" : "rigaincrocidispari";
					w.append("<tr class='" + rowClass + "'>");
					w.append("<td class='cellasquadra'>"+d.nomiSquadre.get(d.idSquadre[j])+"</td>");
					
					int puntiRealiSquadra = d.puntiRealiCampionato[j]; 
					
					for (int k=0; k<d.idSquadre.length; k++) {
						int pts = d.matricePuntiSimulazioneCalendari[j][k];
						
						if (j == k) {
							w.append("<td class='t-xxsDiagonale'>" + pts + "</td>");
						} else {
							String cellClass = "t-xxsNeutro"; 
							if (pts < puntiRealiSquadra) cellClass = "t-xxsRosso"; 
							else if (pts > puntiRealiSquadra) cellClass = "t-xxsVerde"; 
							
							w.append("<td class='" + cellClass + "'>" + pts + "</td>");
						}
					}
					w.append("</tr>");
				}
				w.append("</table>");
				w.append("</td>");
				
				// CLASSIFICA AVULSA
				w.append("<td valign='top' width='32%'>");
				w.append("<div class='rigaintestazione'>Classifica avulsa</div>");
				
				w.append("<table class='avulsa-table'>");
				
				Integer[] idxs = new Integer[d.idSquadre.length];
				for(int i=0; i<idxs.length; i++) idxs[i]=i;
				Arrays.sort(idxs, (a,b) -> Integer.compare(d.classificaAvulsaPuntiReali[b], d.classificaAvulsaPuntiReali[a]));
				
				for(int k=0; k<idxs.length; k++) {
					int i = idxs[k];
					w.append("<tr class='avulsa-row'>");
					w.append("<td class='avulsa-rank'>"+(k+1)+"</td>");
					w.append("<td class='avulsa-team'>"+d.nomiSquadre.get(d.idSquadre[i])+"</td>");
					w.append("<td class='avulsa-points'>"+d.classificaAvulsaPuntiReali[i]+"</td>");
					w.append("<td class='avulsa-perf' title='Performance Index'>"+String.format("%.2f", d.sommaMedieAvulsa[i])+"</td>");
					w.append("</tr>");
				}
				w.append("</table>");
				w.append("</td></tr></table>");
				
				// --- STATISTICHE ---
				
				int numSq = d.idSquadre.length;
				class CalStat { int id; String nome; int delta; }
				List<CalStat> calStats = new ArrayList<>();
				for (int col=0; col<numSq; col++) {
					int sommaColonna = 0;
					for (int row=0; row<numSq; row++) sommaColonna += d.matricePuntiSimulazioneCalendari[row][col];
					int puntiTitolare = d.puntiRealiCampionato[col];
					int delta = sommaColonna - (puntiTitolare * numSq);
					CalStat cs = new CalStat(); cs.id = col; cs.nome = d.nomiSquadre.get(d.idSquadre[col]); cs.delta = delta;
					calStats.add(cs);
				}
				Collections.sort(calStats, (a,b) -> Integer.compare(b.delta, a.delta)); 
				
				class Stat { int id; String nome; double delta; }
				List<Stat> statsLuck = new ArrayList<>();
				List<Stat> statsOverrated = new ArrayList<>();
				
				for (int row=0; row<numSq; row++) {
					int reali = d.puntiRealiCampionato[row];
					double sommaRiga = 0;
					for(int c=0; c<numSq; c++) sommaRiga += d.matricePuntiSimulazioneCalendari[row][c];
					double media = sommaRiga / numSq;
					Stat s = new Stat(); s.id = row; s.nome = d.nomiSquadre.get(d.idSquadre[row]);
					s.delta = reali - media; 
					statsLuck.add(s);
					Stat s2 = new Stat(); s2.id = row; s2.nome = s.nome;
					s2.delta = reali - d.sommaMedieAvulsa[row];
					statsOverrated.add(s2);
				}
				Collections.sort(statsLuck, (a,b) -> Double.compare(b.delta, a.delta));
				Collections.sort(statsOverrated, (a,b) -> Double.compare(b.delta, a.delta));
				
				w.append("<br><div class='rigaintestazione' style='text-align:left; padding-left:10px;'>Curiosità & statistiche</div>");
				w.append("<table class='ClassTabellaincroci' width='100%' style='margin-top:0;'>");
				
				// Riga 1
				w.append("<tr class='rigaincrocipari'><td class='stat-label'>Squadra più fortunata (Punti reali > Media calendari)</td>");
				w.append("<td class='stat-value'><span class='t-statVerde'>"+statsLuck.get(0).nome+"</span></td>");
				w.append("<td class='stat-diff'>Diff: +"+String.format("%.2f", statsLuck.get(0).delta)+"</td></tr>");
				
				// Riga 2
				w.append("<tr class='rigaincrocidispari'><td class='stat-label'>Squadra più sfortunata (Punti reali < Media calendari)</td>");
				w.append("<td class='stat-value'><span class='t-statRosso'>"+statsLuck.get(statsLuck.size()-1).nome+"</span></td>");
				w.append("<td class='stat-diff'>Diff: "+String.format("%.2f", statsLuck.get(statsLuck.size()-1).delta)+"</td></tr>");

				// Riga 3
				w.append("<tr class='rigaincrocipari'><td class='stat-label'>Calendario più facile (la maggioranza avrebbe fatto più punti)</td>");
				w.append("<td class='stat-value'><span class='t-statVerde'>"+calStats.get(0).nome+"</span></td>");
				w.append("<td class='stat-diff'>Idx: +"+calStats.get(0).delta+"</td></tr>");
				
				// Riga 4
				w.append("<tr class='rigaincrocidispari'><td class='stat-label'>Calendario più difficile (la maggioranza avrebbe fatto meno punti)</td>");
				w.append("<td class='stat-value'><span class='t-statRosso'>"+calStats.get(calStats.size()-1).nome+"</span></td>");
				w.append("<td class='stat-diff'>Idx: "+calStats.get(calStats.size()-1).delta+"</td></tr>");
				
				// Riga 5
				w.append("<tr class='rigaincrocipari'><td class='stat-label'>Squadra 'La fortuna è cieca' (Reali >> Performance)</td>");
				w.append("<td class='stat-value'><span class='t-statVerde'>"+statsOverrated.get(0).nome+"</span></td>");
				w.append("<td class='stat-diff'>Gap: +"+String.format("%.2f", statsOverrated.get(0).delta)+"</td></tr>");
				
				// Riga 6
				w.append("<tr class='rigaincrocidispari'><td class='stat-label'>Squadra 'La sfiga ci vede benissimo' (Reali << Performance)</td>");
				w.append("<td class='stat-value'><span class='t-statRosso'>"+statsOverrated.get(statsOverrated.size()-1).nome+"</span></td>");
				w.append("<td class='stat-diff'>Gap: "+String.format("%.2f", statsOverrated.get(statsOverrated.size()-1).delta)+"</td></tr>");
				
				w.append("</table>");
				w.append("</div><br>");
			}
			
			w.append("<br><center><font class='cpr'>Powered by InvertiCal (remake)</font></center>");
			w.append("</body></html>");
			w.flush(); w.close();
		} catch (IOException e) { e.printStackTrace(); }
	}
	
	// --- NUOVO CSS DEFINITIVO ---
	private static void createDefaultCssFile(String outputRoot) {
		File f = new File(outputRoot + "incroci.css");
		try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
			w.write("body { font-family: 'Open Sans', 'Roboto', sans-serif; background-color: #f0f2f5; color: #333; margin: 0; padding: 20px; }\n");
			
			w.write(".container_competizione { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 30px; border-top: 4px solid #003366; }\n");
			w.write(".titletext { color: #003366; font-size: 20px; font-weight: 700; margin: 0 0 20px 0; border-bottom: 1px solid #eee; padding-bottom: 10px; }\n");
			
			// TABELLA MATRICE
			w.write(".ClassTabellaincroci { width: 100%; border-collapse: collapse; font-size: 11px; margin-bottom: 1rem; }\n");
			w.write(".ClassTabellaincroci th, .ClassTabellaincroci td { padding: 4px; vertical-align: middle; border: 1px solid #e0e0e0; }\n");
			w.write(".rigaintestazione { background-color: #343a40; color: #ffffff; text-align: center; font-weight: 600; font-size: 13px; letter-spacing: 0.3px; border-radius: 3px 3px 0 0; padding: 8px; }\n");
			w.write(".rigasquadre { background-color: #f8f9fa; color: #495057; font-weight: 700; text-align: center; border-bottom: 2px solid #dee2e6; height: auto; }\n");
			
			// NOMI SQUADRE HEADER
			w.write(".header-squadra { font-size: 10px; line-height: 1.1; word-wrap: break-word; white-space: normal; vertical-align: bottom; padding: 5px 2px; width: 60px; max-width: 70px; }\n");
			
			w.write(".cellasquadra { font-weight: 600; color: #2c3e50; text-align: left; background-color: #fdfdfd; padding-left: 8px; white-space: nowrap; }\n");
			
			// CELLE COLORATE
			w.write(".t-xxsVerde { background-color: #d4edda; color: #000; font-weight: 700; text-align: center; }\n");
			w.write(".t-xxsRosso { background-color: #f8d7da; color: #000; font-weight: 700; text-align: center; }\n");
			w.write(".t-xxsNeutro { background-color: #fffde7; color: #000; text-align: center; }\n");
			w.write(".t-xxsDiagonale { background-color: #ffffff; color: #000; font-weight: 700; text-align: center; border: 2px solid #eee; }\n");
			
			// STATISTICHE
			w.write(".stat-label { font-size: 11px; padding-left: 10px; width: 60%; }\n");
			w.write(".stat-value { width: 25%; text-align: center; }\n");
			w.write(".stat-diff { font-size: 11px; text-align: right; padding-right: 20px; width: 15%; white-space: nowrap; }\n");
			
			w.write(".t-statVerde { background-color: #28a745; color: white; font-weight: 600; padding: 3px 8px; border-radius: 12px; font-size: 11px; display: inline-block; width: 90%; box-sizing: border-box; }\n");
			w.write(".t-statRosso { background-color: #dc3545; color: white; font-weight: 600; padding: 3px 8px; border-radius: 12px; font-size: 11px; display: inline-block; width: 90%; box-sizing: border-box; }\n");
			
			// CLASSIFICA AVULSA
			w.write(".avulsa-table { width: 100%; border-collapse: collapse; font-family: 'Open Sans', sans-serif; margin-top: 10px; }\n");
			w.write(".avulsa-row { border-bottom: 1px solid #dc3545; background-color: #fff; height: 35px; }\n");
			w.write(".avulsa-rank { color: #dc3545; font-weight: 700; font-size: 14px; width: 30px; text-align: center; }\n");
			w.write(".avulsa-team { color: #003366; font-weight: 600; font-size: 13px; text-align: left; padding-left: 10px; }\n");
			w.write(".avulsa-points { color: #003366; font-size: 14px; text-align: right; padding-right: 5px; font-weight: 700; width: 40px; }\n");
			w.write(".avulsa-perf { color: #6c757d; font-size: 11px; text-align: right; padding-right: 10px; width: 50px; font-weight: 400; }\n");
			
			w.write(".cpr { font-size: 11px; color: #999; margin-top: 20px; }\n");
			
		} catch (IOException e) { e.printStackTrace(); }
	}

	private static String calcolaRisPerRender(Regole r, String squadra1, String squadra2, boolean home, Match match) {
		String result = squadra1+ " - " + squadra2 + " "+(home?"(C)":"")+ "<br><br>";
		result += "parz: "+match.squadra1.parziale + "-"+ match.squadra2.parziale+"<br>";
		return result; 
	}

	private static void renderHTML(String[][] risPerRender, String filename, Integer[] squadreArray, Hashtable<Integer,String> nomiteam) throws IOException {
		// UGO: Sostituito FileWriter
		BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8));
		w.append("<html><body><table border='1' cellpadding='3' cellspacing='0'><tr><td>&nbsp;</td>");
		for (int k=0; k<squadreArray.length; k++) w.append("<td>"+nomiteam.get(squadreArray[k])+"</td>");
		w.newLine(); w.append("</tr>");
		for(int j=0; j<squadreArray.length; j++){
			w.append("<tr><td>"+nomiteam.get(squadreArray[j])+"</td>");
			for (int k=0; k<squadreArray.length; k++){
				w.append("<td>"+(j==k?"<b>":"")+risPerRender[j][k]+(j==k?"</b>":"")+"</td>");
			}
			w.append("</tr>"); w.newLine();
		}
		w.append("</table></body></html>"); w.flush(); w.close();
	}

	private static void renderHTML(int[][] superTotalePunti, String filename, Integer[] squadreArray, Hashtable<Integer,String> nomiteam) throws IOException {
		// UGO: Sostituito FileWriter
		BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8));
		w.append("<html><body><table border='1' cellpadding='3' cellspacing='0'><tr><td>&nbsp;</td>");
		for (int k=0; k<squadreArray.length; k++) w.append("<td>"+nomiteam.get(squadreArray[k])+"</td>");
		w.newLine(); w.append("</tr>");
		for(int j=0; j<squadreArray.length; j++){
			w.append("<tr><td>"+nomiteam.get(squadreArray[j])+"</td>");
			for (int k=0; k<squadreArray.length; k++){
				w.append("<td>"+(j==k?"<b>":"")+superTotalePunti[j][k]+(j==k?"</b>":"")+"</td>");
			}
			w.append("</tr>"); w.newLine();
		}
		w.append("</table></body></html>"); w.flush(); w.close();
	}
	
	private static void renderClassificaAvulsaHTML(int[] totale, double[] sommaMedie, ArrayList<Integer> giornate, String filename, Integer[] squadreArray, Hashtable<Integer,String> nomiteam) throws IOException {
		// UGO: Sostituito FileWriter
		BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8));
		w.append("<html><head><title>Classifica Avulsa</title></head><body><h1>Classifica Avulsa</h1><table border='1'><tr><th>Squadra</th><th>Punti</th></tr>");
		for (int idx=0; idx<squadreArray.length; idx++) w.append("<tr><td>"+nomiteam.get(squadreArray[idx])+"</td><td>"+totale[idx]+"</td></tr>");
		w.append("</table></body></html>"); w.flush(); w.close();
	}
}