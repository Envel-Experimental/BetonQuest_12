package pl.betoncraft.betonquest.compatibility.skillapi;

import pl.betoncraft.betonquest.BetonQuest;
import pl.betoncraft.betonquest.compatibility.Integrator;


@SuppressWarnings("PMD.CommentRequired")
public class SkillAPIIntegrator implements Integrator {

    private final BetonQuest plugin;

    public SkillAPIIntegrator() {
        plugin = BetonQuest.getInstance();
    }

    @Override
    public void hook() {
        plugin.registerConditions("skillapiclass", SkillAPIClassCondition.class);
        plugin.registerConditions("skillapilevel", SkillAPILevelCondition.class);
        new SkillAPIKillListener();
    }

    @Override
    public void reload() {
        // Empty
    }

    @Override
    public void close() {
        // Empty
    }

}
