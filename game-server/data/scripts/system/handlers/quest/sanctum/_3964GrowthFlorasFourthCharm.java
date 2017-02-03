package quest.sanctum;

import static com.aionemu.gameserver.model.DialogAction.*;

import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.QuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/**
 * Talk with Erdos (203740). Bring the Yellow Aether Powder (186000090) and Kinah (90000) to Flora (798384).
 * 
 * @author undertrey
 * @modified vlog
 */
public class _3964GrowthFlorasFourthCharm extends QuestHandler {

	public _3964GrowthFlorasFourthCharm() {
		super(3964);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(798384).addOnQuestStart(questId);
		qe.registerQuestNpc(798384).addOnTalkEvent(questId);
		qe.registerQuestNpc(203740).addOnTalkEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		final Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);

		int targetId = 0;
		if (env.getVisibleObject() instanceof Npc)
			targetId = ((Npc) env.getVisibleObject()).getNpcId();

		if (qs == null || qs.isStartable()) {
			if (targetId == 798384) { // Flora
				if (env.getDialogActionId() == QUEST_SELECT)
					return sendQuestDialog(env, 1011);
				else
					return sendQuestStartDialog(env, 182206111, 1);
			}
		} else if (qs.getStatus() == QuestStatus.START) {
			int var = qs.getQuestVarById(0);
			switch (targetId) {
				case 203740: // Erdos
					switch (env.getDialogActionId()) {
						case QUEST_SELECT:
							if (var == 0)
								return sendQuestDialog(env, 1352);
							return false;
						case SETPRO1:
							return defaultCloseDialog(env, 0, 1, 0, 0, 182206111, 1); // 1
					}
					return false;
				case 798384: // Flora
					switch (env.getDialogActionId()) {
						case QUEST_SELECT:
							if (var == 1) {
								removeQuestItem(env, 182206111, 1);
								return sendQuestDialog(env, 2375);
							}
							return false;
						case CHECK_USER_HAS_QUEST_ITEM:
							long itemAmount = player.getInventory().getItemCountByItemId(186000090);
							if (var == 1 && itemAmount >= 1 && player.getInventory().tryDecreaseKinah(90000)) {
								player.getInventory().decreaseKinah(90000);
								removeQuestItem(env, 186000090, 1);
								changeQuestStep(env, 1, 1, true); // reward
								return sendQuestDialog(env, 5);
							} else
								return sendQuestDialog(env, 2716);
						case FINISH_DIALOG:
							return defaultCloseDialog(env, 1, 1);
					}
			}
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 798384) { // Flora
				return sendQuestEndDialog(env);
			}
		}
		return false;
	}
}
