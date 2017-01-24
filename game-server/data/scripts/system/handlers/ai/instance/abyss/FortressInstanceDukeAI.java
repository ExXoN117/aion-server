package ai.instance.abyss;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.skill.NpcSkillEntry;
import com.aionemu.gameserver.utils.MathUtil;

import ai.AggressiveNpcAI;

/**
 * Created on June 24th, 2016
 *
 * @author Estrayl
 * @since AION 4.8
 */
@AIName("fortress_instance_duke")
public class FortressInstanceDukeAI extends AggressiveNpcAI {

	@Override
	public void onEndUseSkill(NpcSkillEntry usedSkill) {
		if (usedSkill.getSkillId() == 18003)
			spawn(284978, getOwner().getX(), getOwner().getY(), getOwner().getZ(), getOwner().getHeading());
	}
	
	private void deleteSummons() {
		getPosition().getWorldMapInstance().getNpcs().stream().filter(n -> MathUtil.isBetween(284978, 284981, n.getNpcId()))
		.forEach(n -> n.getController().delete());
	}

	@Override
	protected void handleDied() {
		super.handleDied();
		deleteSummons();
	}

	@Override
	protected void handleDespawned() {
		super.handleDespawned();
		deleteSummons();
	}
}