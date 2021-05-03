package edu.umich.andykong.ptmshepherd.core;

import edu.umich.andykong.ptmshepherd.PTMShepherd;

import java.lang.reflect.Array;
import java.util.*;

public class Spectrum implements Comparable<Spectrum> {

	public int scanNum;
	int charge;
	double precursorMass, rt;
	double monoMass, targetMass;
	float [] peakMZ;
	float [] peakInt;
	double norm;
	public String scanName;
	public int msLevel;
	public double[] averageFragMass;
	public double averageIonMass;
	
	static float [] fact;

	//this constructor parses batmass compatible filetypes
	public Spectrum(int nfrags) {
		peakMZ = new float[nfrags];
		peakInt = new float[nfrags];
		norm = -1;
		if(fact == null) {
			fact = new float[128];
			fact[0] = 0;
			for(int i = 1; i < 128; i++)
				fact[i] = (float)(fact[i-1] + Math.log(i));
		}
	}

	//this constructor parses MZBin files and MGF files
	public Spectrum(String scanname, int scannum, int z, int mslevel, double precursormass, double rettime, float[] peakmz, float[] peakint) {
		scanName = scanname;
		scanNum = scannum;
		charge = z;
		precursorMass = precursormass;
		rt = rettime;
		peakMZ = peakmz;
		peakInt = peakint;
		msLevel = mslevel;
		norm = -1;
		if(fact == null) {
			fact = new float[128];
			fact[0] = 0;
			for(int i = 1; i < 128; i++)
				fact[i] = (float)(fact[i-1] + Math.log(i));
		}
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(scanNum+" -");
		for(int i = 0; i < peakMZ.length; i++)
			sb.append(String.format(" [%.2f %.2f]",peakMZ[i],peakInt[i]));
		return sb.toString();
	}
	
	public double getSimilarity(Spectrum s, double ppmTol) {
		double sum = 0;
		double ppm = ppmTol*(1.0/1000000);
		int pos = 0;
		for(int i = 0; i < peakMZ.length; i++) {
			for(int j = pos; j < s.peakMZ.length; j++) {
				if(s.peakMZ[j] < peakMZ[i]*(1-ppm))
					pos = j;
				else if(s.peakMZ[j] > peakMZ[i]*(1+ppm))
					break;
				else {
					sum += peakInt[i]*s.peakInt[j];
				}
			}
		}
		return sum;
	}
	
	public double cosineSimilarity(Spectrum s, double ppmTol) {
		double n1 = norm(ppmTol);
		double n2 = s.norm(ppmTol);
		return (getSimilarity(s, ppmTol) / (n1 * n2));
	}
	
	public double averageSimilarity(Collection<Spectrum> specs, double ppmTol) {
		double sum = 0;
		for(Spectrum s : specs) {
			if (s == null) {
				continue;
			}
			sum += cosineSimilarity(s, ppmTol);
		}
		return sum / specs.size();
	}
	
	public double norm(double ppmTol) {
		if(norm >= 0)
			return norm;
		norm = Math.sqrt(getSimilarity(this, ppmTol));
		return norm;
	}
	
	public void condition(int topN, double ratio) {
		ArrayList<Peak> peaks = new ArrayList<Peak>();
		float mv = 0;
		for(int i = 0 ; i < peakInt.length; i++) {
			peaks.add(new Peak(peakMZ[i],peakInt[i]));
			if(peakInt[i] > mv)
				mv = peakInt[i];
		}
				
		Collections.sort(peaks, new Comparator<Peak>() {
			public int compare(Peak o1, Peak o2) {
				return -1*Float.valueOf(o1.Int).compareTo(o2.Int);
			}
		});
		
		while(peaks.size() > topN)
			peaks.remove(peaks.size()-1);
		while(peaks.size() > 0 && (peaks.get(peaks.size()-1).Int < peaks.get(0).Int*ratio))
			peaks.remove(peaks.size()-1);
		
		Collections.sort(peaks, new Comparator<Peak>() {
			public int compare(Peak o1, Peak o2) {
				return Float.valueOf(o1.MZ).compareTo(o2.MZ);
			}
		});
		
		peakMZ = new float[peaks.size()];
		peakInt = new float[peaks.size()];
		for(int i = 0; i < peaks.size(); i++) {
			peakMZ[i] = peaks.get(i).MZ;
			peakInt[i] = (100*peaks.get(i).Int) / mv;
		}
	}

	public void conditionOptNorm(int topN, double ratio, boolean normalize) {
		ArrayList<Peak> peaks = new ArrayList<Peak>();
		float mv = 0;
		for(int i = 0 ;i < peakInt.length; i++) {
			peaks.add(new Peak(peakMZ[i],peakInt[i]));
			if(peakInt[i] > mv)
				mv = peakInt[i];
		}

		Collections.sort(peaks, new Comparator<Peak>() {
			public int compare(Peak o1, Peak o2) {
				return -1*Float.valueOf(o1.Int).compareTo(o2.Int);
			}
		});

		while(peaks.size() > topN)
			peaks.remove(peaks.size()-1);
		while(peaks.size() > 0 && (peaks.get(peaks.size()-1).Int < peaks.get(0).Int*ratio))
			peaks.remove(peaks.size()-1);

		Collections.sort(peaks, new Comparator<Peak>() {
			public int compare(Peak o1, Peak o2) {
				return Float.valueOf(o1.MZ).compareTo(o2.MZ);
			}
		});

		peakMZ = new float[peaks.size()];
		peakInt = new float[peaks.size()];
		for(int i = 0; i < peaks.size(); i++) {
			peakMZ[i] = peaks.get(i).MZ;
			if (normalize)
				peakInt[i] = (100*peaks.get(i).Int) / mv;
			else
				peakInt[i] = peaks.get(i).Int;
		}
	}
	
	public float getHyper(String seq, float [] mods, double ppmTol) {
		float score = 0.0f;
		float iB = 0.0f, iY = 0.0f;
		int nB = 0, nY = 0;
		int maxCharge = Math.min(Integer.parseInt(PTMShepherd.getParam("spectra_maxfragcharge")), charge);

		float [] aaMasses = AAMasses.monoisotopic_masses;
		float [] fragTypeShifts = AAMasses.ionTypeShifts;

		float cM = 0;
		double tol;
		int fP = 0;
		int cLen = seq.length();

		ArrayList<Character> nIonTypes = new ArrayList<>();
		ArrayList<Character> cIonTypes = new ArrayList<>();

		if (PTMShepherd.getParam("iontype_a").trim().equals("1"))
			nIonTypes.add('a');
		if (PTMShepherd.getParam("iontype_b").trim().equals("1"))
			nIonTypes.add('b');
		if (PTMShepherd.getParam("iontype_c").trim().equals("1"))
			nIonTypes.add('c');
		if (PTMShepherd.getParam("iontype_x").trim().equals("1"))
			cIonTypes.add('x');
		if (PTMShepherd.getParam("iontype_y").trim().equals("1"))
			cIonTypes.add('y');
		if (PTMShepherd.getParam("iontype_z").trim().equals("1"))
			cIonTypes.add('z');

		float nTermMass;
		for (Character iType : nIonTypes) {
			nTermMass = fragTypeShifts[iType - 'a'];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) {
				double cmass = AAMasses.monoisotopic_nterm_mass + nTermMass;
				fP = 0;
				for (int i = 0; i < cLen - 1; i++) {
					cmass += (aaMasses[seq.charAt(i) - 'A'] + mods[i]) / ccharge;
					tol = cmass * (ppmTol / 1000000.0);
					while (fP < peakMZ.length && peakMZ[fP] < (cmass - tol))
						fP++;
					if (fP < peakMZ.length && Math.abs(peakMZ[fP] - cmass) < tol) {
						cM = peakInt[fP];
					} else
						cM = 0;
					if (cM > 0) {
						nB++;
						iB += cM;
					}
				}
			}
		}
		float cTermMass;
		for (Character iType : cIonTypes) {
			cTermMass = fragTypeShifts[iType - 'x' + 3];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) {
				//double cmass = (AAMasses.monoisotopic_cterm_mass + (ccharge+1)*AAMasses.monoisotopic_nterm_mass) / ccharge;
				double cmass = (cTermMass + ccharge * AAMasses.monoisotopic_nterm_mass) / ccharge;
				fP = 0;
				for (int i = 0; i < cLen - 1; i++) {
					cmass += (aaMasses[seq.charAt(cLen - 1 - i) - 'A'] + mods[cLen - 1 - i]) / ccharge;
					tol = cmass * (ppmTol / 1000000.0);
					while (fP < peakMZ.length && peakMZ[fP] < (cmass - tol))
						fP++;
					if (fP < peakMZ.length && Math.abs(peakMZ[fP] - cmass) < tol) {
						cM = peakInt[fP];
					} else
						cM = 0;
					if (cM > 0) {
						nY++;
						iY += cM;
					}
				}
			}
		}
		
		score = fact[nB] + fact[nY];
		if(iB > 1)
			score += Math.log(iB);
		if(iY > 1)
			score += Math.log(iY);
		
		return score;
	}
	
	public int getFrags(String seq, float [] mods, double ppmTol) {
		float iB = 0.0f, iY = 0.0f;
		int nB = 0, nY = 0;
		int maxCharge = (charge==2)?1:2;

		float [] aaMasses = AAMasses.monoisotopic_masses;
		float [] fragTypeShifts = AAMasses.ionTypeShifts;

		float cM = 0;
		double tol;
		int fP = 0;
		int cLen = seq.length();

		ArrayList<Character> nIonTypes = new ArrayList<>();
		ArrayList<Character> cIonTypes = new ArrayList<>();

		if (PTMShepherd.getParam("iontype_a").trim().equals("1"))
			nIonTypes.add('a');
		if (PTMShepherd.getParam("iontype_b").trim().equals("1"))
			nIonTypes.add('b');
		if (PTMShepherd.getParam("iontype_c").trim().equals("1"))
			nIonTypes.add('c');
		if (PTMShepherd.getParam("iontype_x").trim().equals("1"))
			cIonTypes.add('x');
		if (PTMShepherd.getParam("iontype_y").trim().equals("1"))
			cIonTypes.add('y');
		if (PTMShepherd.getParam("iontype_z").trim().equals("1"))
			cIonTypes.add('z');

		float nTermMass;
		for (Character iType : nIonTypes) {
			nTermMass = fragTypeShifts[iType - 'a'];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) {
				//double cmass = AAMasses.monoisotopic_nterm_mass;
				double cmass = AAMasses.monoisotopic_nterm_mass + nTermMass;
				fP = 0;
				for (int i = 0; i < cLen - 1; i++) {
					cmass += (aaMasses[seq.charAt(i) - 'A'] + mods[i]) / ccharge;
					tol = cmass * (ppmTol / 1000000.0);
					while (fP < peakMZ.length && peakMZ[fP] < (cmass - tol))
						fP++;
					if (fP < peakMZ.length && Math.abs(peakMZ[fP] - cmass) < tol) {
						cM = peakInt[fP];
					} else
						cM = 0;
					if (cM > 0) {
						nB++;
						iB += cM;
					}
				}
			}
		}
		float cTermMass;
		for (Character iType : cIonTypes) {
			cTermMass = fragTypeShifts[iType - 'x' + 3];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) {
				//double cmass = (AAMasses.monoisotopic_cterm_mass + (ccharge + 1) * AAMasses.monoisotopic_nterm_mass) / ccharge;
				double cmass = (cTermMass + ccharge * AAMasses.monoisotopic_nterm_mass) / ccharge;
				fP = 0;
				for (int i = 0; i < cLen - 1; i++) {
					cmass += (aaMasses[seq.charAt(cLen - 1 - i) - 'A'] + mods[cLen - 1 - i]) / ccharge;
					tol = cmass * (ppmTol / 1000000.0);
					while (fP < peakMZ.length && peakMZ[fP] < (cmass - tol))
						fP++;
					if (fP < peakMZ.length && Math.abs(peakMZ[fP] - cmass) < tol) {
						cM = peakInt[fP];
					} else
						cM = 0;
					if (cM > 0) {
						nY++;
						iY += cM;
					}
				}
			}
		}
		return nB+nY;
	}

	public double findIon(double ion, double ppmTol) {
		double ppmRange = ppmTol * (1.0 / 1000000) * ion;
		double max = ion + ppmRange;
		double ionIntensity = 0;
		for (int i = 0; i < peakMZ.length; i++) {
			if (peakMZ[i] > max)
				break;
			if (Math.abs(peakMZ[i] - ion) < ppmRange)
				ionIntensity += peakInt[i];
		}
		return ionIntensity;
	}

	public double findIonNeutral(double neutralIonMass, double ppmTol) {
		double ionIntensity = 0;
		for (int z = 1; z <= charge; z++) {
			double ion = (neutralIonMass + (1.00727 * (double) z)) / (double) z;
			double ppmRange = ppmTol * (1.0 / 1000000) * ion;
			double max = ion + ppmRange;
			for (int i = 0; i < peakMZ.length; i++) {
				if (peakMZ[i] > max)
					break;
				if (Math.abs(peakMZ[i] - ion) < ppmRange)
					ionIntensity += peakInt[i];
			}
		}
		return ionIntensity;
	}

	public double findBasePeakInt() {
		double bpInt = 0;
		for (int i = 0; i < peakInt.length; i++) {
			if (peakInt[i] > bpInt)
				bpInt = peakInt[i];
		}
		return bpInt;
	}

	public float[][] calcImmoniumPeaks(int min, int max, String seq, float[] mods, String ionTypes, int maxCharge, float dmass) { //todo I don't think these mins and maxes are very well informed
		ArrayList<Float> knownPeaks = calculatePeptideFragments(seq, mods, ionTypes, maxCharge);
		ArrayList<Float> shiftedPeaks = new ArrayList<>();
		for (Float peak : knownPeaks) //todo this will only work with maxcharge = 1, should be done in above function
			shiftedPeaks.add(peak + dmass);
		knownPeaks.addAll(shiftedPeaks);

		this.averageIonMass = 0;

		ArrayList<Peak> ps = new ArrayList<>();
		for (int i = 0; i < peakMZ.length; i++) {
			if (peakMZ[i] > max)
				break;
			if (peakMZ[i] > min) {
				boolean skipFlag = false;
				for (Float peak : knownPeaks) {
					if (Math.abs(peak - peakMZ[i]) < 0.01) { //todo ppm tol
						skipFlag = true;
						break;
					}
				}
				if (skipFlag == false) {
					ps.add(new Peak(peakMZ[i], peakInt[i]));
					this.averageIonMass += peakMZ[i];
				}
			}
		}
		float[][] peaks =  new float[ps.size()][2];
		for (int i = 0; i < ps.size(); i++) {
			peaks[i][0] = ps.get(i).MZ;
			peaks[i][1] = ps.get(i).Int;
		}
		return peaks;
	}

	public float[][] calcCapYPeaks(String seq, float[] mods, String ionTypes, int maxCharge, float pepMass) { //todo include tolerance
		ArrayList<Float> knownPeaks = calculatePeptideFragments(seq, mods, ionTypes, maxCharge);
		ArrayList<Peak> ps = new ArrayList<>();
		float shift;

		//for (int z = 1; z <= charge; z++) { //todo
		for (int z = 1; z <= 1; z++) {
			double adjPepMass = (pepMass + 1.00727 * z) / z;
			for (int i = 0; i < peakMZ.length; i++) {
				boolean skipFlag = false;
				for (Float peak : knownPeaks) {
					if (Math.abs(peak - peakMZ[i]) < 0.01) { //todo ppm tol
						skipFlag = true;
						break;
					}
				}
				//if (Math.abs(peakMZ[i] - adjPepMass < 0.01))
				//	skipFlag = true;
				if (skipFlag == false)
					ps.add(new Peak((float)((peakMZ[i] - adjPepMass) * z), peakInt[i]));
			}
		}
		float[][] peaks =  new float[ps.size()][2];
		for (int i = 0; i < ps.size(); i++) {
			peaks[i][0] = ps.get(i).MZ;
			peaks[i][1] = ps.get(i).Int;
		}
		return peaks;
	}

	private ArrayList<Float> calculatePeptideFragments(String seq, float[] mods, String ionTypes, int maxCharge) {
		ArrayList<Float> knownFrags = new ArrayList<>();

		ArrayList<Character> nIonTypes = new ArrayList<>();
		ArrayList<Character> cIonTypes = new ArrayList<>();
		for (int i = 0; i < ionTypes.length(); i++) {
			char curIonType = ionTypes.charAt(i);
			if (curIonType == 'a' || curIonType == 'b' || curIonType == 'c')
				nIonTypes.add(curIonType);
			else if (curIonType == 'x' || curIonType == 'y' || curIonType == 'z')
				cIonTypes.add(curIonType);
		}

		float [] aaMasses = AAMasses.monoisotopic_masses;
		float [] fragTypeShifts = AAMasses.ionTypeShifts;
		int cLen = seq.length();

		float nTermMass;
		for (Character iType : nIonTypes) {
			nTermMass = fragTypeShifts[iType - 'a'];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) { //loop through charge states
				float cmass = AAMasses.monoisotopic_nterm_mass + nTermMass;
				for (int i = 0; i < cLen - 1; i++) { //loop through positions on the peptide
					cmass += (aaMasses[seq.charAt(i) - 'A'] + mods[i]) / ccharge;
					knownFrags.add(cmass);
				}
			}
		}
		float cTermMass;
		for (Character iType : cIonTypes) {
			cTermMass = fragTypeShifts[iType - 'x' + 3];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) {
				float cmass = (cTermMass + ccharge * AAMasses.monoisotopic_nterm_mass) / ccharge;
				for (int i = 0; i < cLen - 1; i++) {
					cmass += (aaMasses[seq.charAt(cLen - 1 - i) - 'A'] + mods[cLen - 1 - i]) / ccharge;
					knownFrags.add(cmass);
				}
			}
		}

		return knownFrags;
	}

	public HashMap<Character, float[][]> safeold_calcSquigglePeaks(float ppmTol, String seq, float[] mods, String ionTypes, int maxCharge) { //todo max charge? //todo min max boundaries?
		HashMap<Character, float[][]> squigglePeaks = new HashMap<>();

		ArrayList<Character> nIonTypes = new ArrayList<>();
		ArrayList<Character> cIonTypes = new ArrayList<>();

		for (int i = 0; i < ionTypes.length(); i++) {
			char curIonType = ionTypes.charAt(i);
			if (curIonType == 'a' || curIonType == 'b' || curIonType == 'c')
				nIonTypes.add(curIonType);
			else if (curIonType == 'x' || curIonType == 'y' || curIonType == 'z')
				cIonTypes.add(curIonType);
		}

		ArrayList<Peak> ps = new ArrayList<>();

		//float iB = 0.0f, iY = 0.0f;
		//int nB = 0, nY = 0;
		//int maxCharge = (charge==2)?1:2;
		float [] aaMasses = AAMasses.monoisotopic_masses;
		float [] fragTypeShifts = AAMasses.ionTypeShifts;
        //float[][] tempPeaks;
		int cLen = seq.length();


		//todo see if removing known frags helps .... it helps ish??
		ArrayList<Float> knownFrags = new ArrayList<>();
		/* float nTermMass;
		for (Character iType : nIonTypes) {
			ps = new ArrayList<>();
			nTermMass = fragTypeShifts[iType - 'a'];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) { //loop through charge states
				float cmass = AAMasses.monoisotopic_nterm_mass + nTermMass;
				for (int i = 0; i < cLen - 1; i++) { //loop through positions on the peptide
					cmass += (aaMasses[seq.charAt(i) - 'A'] + mods[i]) / ccharge;
					knownFrags.add(cmass);
				}
			}
		}
		float cTermMass;
		for (Character iType : cIonTypes) {
			ps = new ArrayList<>();
			cTermMass = fragTypeShifts[iType - 'x' + 3];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) {
				float cmass = (cTermMass + ccharge * AAMasses.monoisotopic_nterm_mass) / ccharge;
				for (int i = 0; i < cLen - 1; i++) {
					cmass += (aaMasses[seq.charAt(cLen - 1 - i) - 'A'] + mods[cLen - 1 - i]) / ccharge;
					knownFrags.add(cmass);
				}
			}
		}

		 */
		float nTermMass;
		for (Character iType : nIonTypes) {
		    ps = new ArrayList<>();
			nTermMass = fragTypeShifts[iType - 'a'];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) { //loop through charge states
			    float cmass = AAMasses.monoisotopic_nterm_mass + nTermMass;
				for (int i = 0; i < cLen - 1; i++) { //loop through positions on the peptide
					cmass += (aaMasses[seq.charAt(i) - 'A'] + mods[i]) / ccharge;
					for (int j = 0; j < peakMZ.length; j++) {//loop through peaks in spectrum
						//This block will remove known fragments
						/*
						boolean badFlag = false;
						for (int k = 0; k < knownFrags.size(); k++) {
							if (Math.abs(peakMZ[j] - knownFrags.get(k)) < 0.001) {
								badFlag = true;
								break;
							}
						}
						if (!badFlag)
						 */
							ps.add(new Peak(peakMZ[j] - cmass, peakInt[j]));
						//ps.add(new Peak(peakMZ[j] - cmass, peakInt[j] / (i + 1)));
					}
                }
			}
			squigglePeaks.put(iType, peaksToArray(ps));
		}

		float cTermMass;
		for (Character iType : cIonTypes) {
            ps = new ArrayList<>();
			cTermMass = fragTypeShifts[iType - 'x' + 3];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) {
                float cmass = (cTermMass + ccharge * AAMasses.monoisotopic_nterm_mass) / ccharge;
                for (int i = 0; i < cLen - 1; i++) {
                    cmass += (aaMasses[seq.charAt(cLen - 1 - i) - 'A'] + mods[cLen - 1 - i]) / ccharge;
                    for (int j = 0; j < peakMZ.length; j++) {//loop through peaks in spectrum
                    	//This block will remove known fragments
                    /*
						boolean badFlag = false;
						for (int k = 0; k < knownFrags.size(); k++) {
							if (Math.abs(peakMZ[j] - knownFrags.get(k)) < 0.001) {
								badFlag = true;
								break;
							}
						}
						if (!badFlag)

                     */
							ps.add(new Peak(peakMZ[j] - cmass, peakInt[j]));
						//ps.add(new Peak(peakMZ[j] - cmass, peakInt[j] / (i + 1)));
					}
                }
            }
            squigglePeaks.put(iType, peaksToArray(ps));
		}
		return squigglePeaks;
	}

	public HashMap<Character, float[][]> calcSquigglePeaks(float ppmTol, float dmass, String seq, float[] mods, String ionTypes, int maxCharge) { //todo max charge? //todo min max boundaries?
		HashMap<Character, float[][]> squigglePeaks = new HashMap<>();

		ArrayList<Character> nIonTypes = new ArrayList<>();
		ArrayList<Character> cIonTypes = new ArrayList<>();

		this.averageFragMass = new double[ionTypes.length()];
		int iTypeIndx = 0;

		for (int i = 0; i < ionTypes.length(); i++) {
			char curIonType = ionTypes.charAt(i);
			if (curIonType == 'a' || curIonType == 'b' || curIonType == 'c')
				nIonTypes.add(curIonType);
			else if (curIonType == 'x' || curIonType == 'y' || curIonType == 'z')
				cIonTypes.add(curIonType);
		}

		ArrayList<Peak> ps = new ArrayList<>();

		//float iB = 0.0f, iY = 0.0f;
		//int nB = 0, nY = 0;
		//int maxCharge = (charge==2)?1:2;
		float [] aaMasses = AAMasses.monoisotopic_masses;
		float [] fragTypeShifts = AAMasses.ionTypeShifts;
		//float[][] tempPeaks;
		int cLen = seq.length();

		//int normFac;
		//if (PTMShepherd.getParam("squiggle_norm").equals("1"))
		//	normFac = 1;
		//else
		//normFac = cLen;
		//normFac = 1;

		ArrayList<Float> knownFrags = new ArrayList<>();
		float nTermMass;
		for (Character iType : nIonTypes) {
			ps = new ArrayList<>();
			nTermMass = fragTypeShifts[iType - 'a'];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) { //loop through charge states
				float cmass = AAMasses.monoisotopic_nterm_mass + nTermMass; //todo is this appropriate for multiple charge states??
				for (int i = 0; i < cLen - 1; i++) { //loop through positions on the peptide
					cmass += (aaMasses[seq.charAt(i) - 'A'] + mods[i]) / ccharge;
					knownFrags.add(cmass);
				}
			}
		}
		float cTermMass;
		for (Character iType : cIonTypes) {
			ps = new ArrayList<>();
			cTermMass = fragTypeShifts[iType - 'x' + 3];
			for (int ccharge = 1; ccharge <= maxCharge; ccharge++) {
				float cmass = (cTermMass + ccharge * AAMasses.monoisotopic_nterm_mass) / ccharge;
				for (int i = 0; i < cLen - 1; i++) {
					cmass += (aaMasses[seq.charAt(cLen - 1 - i) - 'A'] + mods[cLen - 1 - i]) / ccharge;
					knownFrags.add(cmass);
				}
			}
		}

		for (Character iType : nIonTypes) {
			ps = new ArrayList<>();
			nTermMass = fragTypeShifts[iType - 'a'];
			for (int j = 0; j < peakMZ.length; j++) {
				boolean skipFlag = false;
				double trueTol = ppmTol * peakMZ[j] / 1000000;
				for (Float ion : knownFrags) {
					if (Math.abs(peakMZ[j] - ion) < trueTol) {
						skipFlag = true;
						break;
					}
				}
				//if (skipFlag)
				//	continue;
				ArrayList<Peak> cPeaksNaked = new ArrayList<>();
				//ArrayList<Peak> cPeaksDmass = new ArrayList<>();
				for (int ccharge = 1; ccharge <= maxCharge; ccharge++) { //loop through charge states
					float cmass = AAMasses.monoisotopic_nterm_mass + nTermMass;
					for (int i = 0; i < cLen - 1; i++) { //loop through positions on the peptide
						cmass += (aaMasses[seq.charAt(i) - 'A'] + mods[i]) / ccharge;
						if (skipFlag)
							cPeaksNaked.add(new Peak(peakMZ[j] - cmass, 0));
						else {
							cPeaksNaked.add(new Peak(peakMZ[j] - cmass, peakInt[j]));
							this.averageFragMass[iTypeIndx] += peakMZ[j];
						}
						//cPeaksDmass.add(new Peak(peakMZ[j] - (cmass + dmass), peakInt[j]));
					}
				}
				//System.out.println(iType+"\t"+ps.size()+"\t"+cPeaksNaked.size());

				//Collections.sort(cPeaksNaked);
				//Collections.sort(cPeaksDmass);
				for(int i = 0; i < cPeaksNaked.size(); i++) {
					ps.add(cPeaksNaked.get(i));
					//cPeaksDmass.get(i).lossToRemainder(dmass);
					//ps.add(cPeaksDmass.get(i));
				}
				//for (Peak p : cPeaks)
				//	System.out.println(p.MZ + "\t" + p.Int);
			}
			squigglePeaks.put(iType, peaksToArray(ps));
			iTypeIndx++;
		}

		for (Character iType : cIonTypes) {
			ps = new ArrayList<>();
			cTermMass = fragTypeShifts[iType - 'x' + 3];
			for (int j = 0; j < peakMZ.length; j++) {
				boolean skipFlag = false;
				double trueTol = ppmTol * peakMZ[j] / 1000000;
				for (Float ion : knownFrags) {
					if (Math.abs(peakMZ[j] - ion) < trueTol) {
						skipFlag = true;
						break;
					}
				}
				//if (skipFlag)
				//	continue;
				ArrayList<Peak> cPeaksNaked = new ArrayList<>();
				//ArrayList<Peak> cPeaksDmass = new ArrayList<>();
				for (int ccharge = 1; ccharge <= maxCharge; ccharge++) {
					float cmass = (cTermMass + ccharge * AAMasses.monoisotopic_nterm_mass) / ccharge;
					for (int i = 0; i < cLen - 1; i++) {
						cmass += (aaMasses[seq.charAt(cLen - 1 - i) - 'A'] + mods[cLen - 1 - i]) / ccharge;
						if (skipFlag)
							cPeaksNaked.add(new Peak(peakMZ[j] - cmass, 0));
						else {
							cPeaksNaked.add(new Peak(peakMZ[j] - cmass, peakInt[j]));
							this.averageFragMass[iTypeIndx] += peakMZ[j];
						}
						//cPeaksDmass.add(new Peak(peakMZ[j] - (cmass + dmass), peakInt[j]));
					}
				}
				//Collections.sort(cPeaksNaked);
				//Collections.sort(cPeaksDmass);
				for(int i = 0; i < cPeaksNaked.size(); i++) {
					ps.add(cPeaksNaked.get(i));
					//cPeaksDmass.get(i).lossToRemainder(dmass);
					//ps.add(cPeaksDmass.get(i));
				}
			}
			squigglePeaks.put(iType, peaksToArray(ps));
			iTypeIndx++;
		}
		return squigglePeaks;
	}

	float[][] peaksToArray (ArrayList<Peak> peaks) {
	    float[][] peakArr = new float[peaks.size()][2];
	    for (int i = 0; i < peaks.size(); i ++) {
            peakArr[i][0] = peaks.get(i).MZ;
            peakArr[i][1] = peaks.get(i).Int;
        }
	    return  peakArr;
    }

	public double getAverageIonMass() {
		return this.averageIonMass;
	}

	public double[] getAverageFragMass() {
		return this.averageFragMass;
	}

	public int compareTo(Spectrum o) {
		return scanNum - o.scanNum;
	}
}

class Peak implements Comparable<Peak> {
	float MZ, Int;
	public Peak(float MZ, float Int) {
		this.MZ = MZ;
		this.Int = Int;
	}
	public void lossToRemainder(float dmass) {
		this.MZ = dmass + this.MZ;
	}
	public int compareTo(Peak p) {
		return Double.compare(Math.abs(this.MZ), Math.abs(p.MZ));
	}
}
