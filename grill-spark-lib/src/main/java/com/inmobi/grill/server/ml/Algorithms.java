package com.inmobi.grill.server.ml;

import com.inmobi.grill.api.GrillException;
import com.inmobi.grill.server.api.ml.Algorithm;
import com.inmobi.grill.server.api.ml.MLTrainer;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Algorithms {
  private final Map<String, Class<? extends MLTrainer>> algorithmClasses =
    new HashMap<String, Class<? extends MLTrainer>>();

  public void register(Class<? extends MLTrainer> trainerClass) {
    algorithmClasses.put(trainerClass.getAnnotation(Algorithm.class).name(),
      trainerClass);
  }

  public MLTrainer getTrainerForName(String name) throws GrillException {
    Class<? extends MLTrainer> trainerClass = algorithmClasses.get(name);
    Algorithm algoAnnotation = trainerClass.getAnnotation(Algorithm.class);
    String description = algoAnnotation.description();

    if (trainerClass == null) {
      return null;
    }

    try {
      Constructor<? extends MLTrainer> trainerConstructor =
        trainerClass.getConstructor(String.class, String.class);
      return trainerConstructor.newInstance(name, description);
    } catch (Exception exc) {
      throw new GrillException("Unable to get trainer: " + name, exc);
    }
  }

  public boolean isAlgoSupported(String name) {
    return algorithmClasses.containsKey(name);
  }

  public List<String> getAlgorithmNames() {
    return new ArrayList<String>(algorithmClasses.keySet());
  }

}
