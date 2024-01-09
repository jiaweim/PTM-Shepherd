/*
 *    Copyright 2022 University of Michigan
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package edu.umich.andykong.ptmshepherd.glyco;


import edu.umich.andykong.ptmshepherd.PTMShepherd;

import java.util.HashMap;

/**
 * Allowed names for glyco residues. Matched to masses in GlycanMasses
 */

public class GlycanResidue implements Comparable<GlycanResidue> {
    public String name;
    public double mass;
    public double[] yProbs;
    public String[] alternateNames;
    public boolean islabile;

    private final static HashMap<String, Integer> residueOrder;
    static {
        residueOrder = new HashMap<>();
        residueOrder.put("HexNAc", 0);
        residueOrder.put("Hex", 1);
        residueOrder.put("Fuc", 2);
        residueOrder.put("NeuAc", 3);
        residueOrder.put("NeuGc", 4);
    }

    public GlycanResidue(String name, double mass, double[] yProbs, String[] altNames, boolean isLabile) {
        this.name = name;
        this.mass = mass;
        this.yProbs = yProbs;
        this.alternateNames = altNames;
        this.islabile = isLabile;
    }

    public static GlycanResidue parseResidue(String line) {
        String[] splits = line.replace("\"", "").split("\t");
        double mass = Double.parseDouble(splits[1]);
        double yProbPlus = getOrDefault(splits[2]);
        double yProbMinus = getOrDefault(splits[3]);
        String[] altNames = splits[4].split(",");
        boolean isLabile = yProbMinus == -1 && yProbPlus == -1;
        if (yProbPlus == -1 ^ yProbMinus == -1) {
            PTMShepherd.die(String.format("Error parsing glycan residue defintions. Residue %s had only 1 Y ion probability defined, but must have 2 (or 0 if labile).", line));
        }
        return new GlycanResidue(splits[0],
                mass,
                new double[]{yProbPlus, yProbMinus},
                altNames,
                isLabile
        );
    }

    // return -1 for any values not provided (not all rules are needed for all residues).
    public static double getOrDefault(String input) {
        if (input.length() > 0) {
            return Double.parseDouble(input);
        } else {
            return -1;
        }
    }

    public String toString() {
        return name;
    }

    public String printParam() {
        String probs = islabile ? "labile" : String.format("%.1f, %.1f", yProbs[0], yProbs[1]);
        return String.format("%s: %.4f %s", name, mass, probs);
    }

    /**
     * Sorting method for glycans by name, preserving the same order as older versions of the code.
     * @param o
     * @return
     */
    @Override
    public int compareTo(GlycanResidue o) {
        if (residueOrder.containsKey(name)) {
            if (residueOrder.containsKey(o.name)) {
                return residueOrder.get(name).compareTo(residueOrder.get(o.name));
            } else {
                return -1;  // return the specifically ordered one first
            }
        } else {
            if (residueOrder.containsKey(o.name)) {
                return 1;
            } else {
                return name.compareTo(o.name);
            }
        }
    }
}
