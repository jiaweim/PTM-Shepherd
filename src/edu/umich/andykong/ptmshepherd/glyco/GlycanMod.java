package edu.umich.andykong.ptmshepherd.glyco;

import edu.umich.andykong.ptmshepherd.PTMShepherd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GlycanMod {
    public String name;
    public double mass;
    public boolean isLabile;
    public boolean isFixed;
    public int maxAllowed;
    public GlycanResidue modResidue;
    public ArrayList<GlycanResidue> requiredResidues;

    /**
     * Parse the basic information about a mod from the glycan_mods.tsv file. Mod settings (max allowed, fixed/variable)
     * come from user params and are set later.
     * @param modTsvLine
     */
    public GlycanMod(String modTsvLine, GlycoParams glycoParams) {
        modResidue = GlycanResidue.parseResidue(modTsvLine);
        name = modResidue.name;
        mass = modResidue.mass;
        String[] splits = modTsvLine.replace("\"", "").split("\t");
        isLabile = Boolean.parseBoolean(splits[9]);
        modResidue.islabile = isLabile;

        // parse required residues (if present)
        requiredResidues = new ArrayList<>();
        if (splits.length > 10) {
            if (!splits[10].matches("")) {
                String[] resSplits = splits[10].split(",");
                for (String residueStr : resSplits) {
                    GlycanResidue residue = glycoParams.findResidueName(residueStr);
                    if (residue != null) {
                        requiredResidues.add(residue);
                    } else {
                        PTMShepherd.die(String.format("Error parsing glycan_mods.tsv: required residue %s in line %s was not recognized. Make sure this glycan residue is in the glycan_residues.tsv and that it is spelled correctly in glycan_mods.tsv and try again.", residueStr, modTsvLine));
                    }
                }
            }
        }
    }
}
