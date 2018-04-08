package com.aionemu.gameserver.services.siege;

import java.util.Collection;
import java.util.List;

import com.aionemu.commons.callbacks.util.GlobalCallbackHelper;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.ai.manager.WalkManager;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.siege.SiegeNpc;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.siege.AgentLocation;
import com.aionemu.gameserver.model.siege.SiegeModType;
import com.aionemu.gameserver.model.siege.SiegeRace;
import com.aionemu.gameserver.model.templates.spawns.SpawnGroup;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.model.templates.spawns.siegespawns.SiegeSpawnTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.services.BaseService;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.services.mail.SiegeResult;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.spawnengine.SpawnHandlerType;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.zone.ZoneName;

/**
 * @author Estrayl
 */
public class AgentSiege extends Siege<AgentLocation> {

	private final AgentDeathListener veilleDeathListener = new AgentDeathListener(this);
	private final AgentDeathListener mastaDeathListener = new AgentDeathListener(this);
	private final SiegeBossDoAddDamageListener veilleDoAddDamageListener = new SiegeBossDoAddDamageListener(this);
	private final SiegeBossDoAddDamageListener mastaDoAddDamageListener = new SiegeBossDoAddDamageListener(this);
	private final AgentAbyssPointsListener apListener = new AgentAbyssPointsListener(this);
	private byte startProgress = 1;
	private SiegeNpc masta, veille;
	private SiegeRace winner;

	/**
	 * Set race for both deathListeners with opposite race.
	 * This will handle better siege end, later.
	 * 
	 * @param siegeLocation
	 */
	public AgentSiege(AgentLocation siegeLocation) {
		super(siegeLocation);
		veilleDeathListener.setRace(SiegeRace.ASMODIANS);
		mastaDeathListener.setRace(SiegeRace.ELYOS);
	}

	@Override
	protected void onSiegeStart() {
		PacketSendUtility.broadcastToWorld(SM_SYSTEM_MESSAGE.STR_MSG_LDF4_ADVANCE_GODELITE_TIME_01());
		getSiegeLocation().setVulnerable(true);
		delayStart();
	}

	private void delayStart() {
		ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				startProgress++;
				if (startProgress == 5) {
					PacketSendUtility.broadcastToWorld(SM_SYSTEM_MESSAGE.STR_MSG_LDF4_ADVANCE_GODELITE_TIME_02());
				} else if (startProgress >= 10) {
					onQuestDistribute();
					onSpawn(); // Should initialize Agents and their flags
					GlobalCallbackHelper.addCallback(apListener);
					return; // Interrupts the task
				}
				delayStart();
			}
		}, 60000);
	}

	@Override
	protected void onSiegeFinish() {
		PacketSendUtility.broadcastToWorld(SM_SYSTEM_MESSAGE.STR_MSG_LDF4_ADVANCE_GODELITE_TIME_03());
		getSiegeLocation().setVulnerable(false);
		GlobalCallbackHelper.removeCallback(apListener);
		removeListeners();
		onDespawn();
		if (winner == null)
			return;
		Race winnerRace = winner == SiegeRace.ELYOS ? Race.ELYOS : Race.ASMODIANS;
		BaseService.getInstance().capture(6113, winnerRace);
		SiegeRace looser = winner == SiegeRace.ELYOS ? SiegeRace.ASMODIANS : SiegeRace.ELYOS;
		sendRewardsToParticipants(getSiegeCounter().getRaceCounter(winner), SiegeResult.OCCUPY);
		sendRewardsToParticipants(getSiegeCounter().getRaceCounter(looser), SiegeResult.FAIL);
	}

	private void onWalkingEvent(Npc npc, String walkerId) {
		if (npc == null)
			return;
		npc.getSpawn().setWalkerId(walkerId);
		WalkManager.startWalking((NpcAI) npc.getAi());
		npc.setState(CreatureState.WALK_MODE);
		PacketSendUtility.broadcastPacket(npc, new SM_EMOTION(npc, EmotionType.START_EMOTE2, 0, npc.getObjectId()));
	}

	private void onQuestDistribute() {
		for (Player player : World.getInstance().getWorldMap(600100000).getMainWorldMapInstance().getPlayersInside()) {
			if (player.isInsideZone(ZoneName.get("DRAGON_LORDS_SHRINE_600100000")) || player.isInsideZone(ZoneName.get("FLAMEBERTH_DOWNS_600100000"))) {
				int questId;
				if (player.getRace() == Race.ELYOS)
					questId = 13744;
				else
					questId = 23744;
				QuestState qs = player.getQuestStateList().getQuestState(questId);
				if (qs == null || qs.isStartable())
					QuestService.startQuest(new QuestEnv(null, player, questId));
			}
		}
	}

	public void onSpawn() {
		List<SpawnGroup> siegeSpawns = DataManager.SPAWNS_DATA.getSiegeSpawnsByLocId(getSiegeLocationId());
		if (siegeSpawns == null)
			return;
		for (SpawnGroup group : siegeSpawns) {
			for (SpawnTemplate template : group.getSpawnTemplates()) {
				SiegeSpawnTemplate siegetemplate = (SiegeSpawnTemplate) template;
				if (siegetemplate.getSiegeRace() == SiegeRace.BALAUR && siegetemplate.getSiegeModType() == SiegeModType.SIEGE) {
					SiegeNpc npc = (SiegeNpc) SpawnEngine.spawnObject(siegetemplate, 1);
					if (npc.getSpawn().getHandlerType() == SpawnHandlerType.BOSS)
						initNpc(npc);
				}
			}
		}
		registerListeners();
	}

	public void onDespawn() {
		Collection<SiegeNpc> npcs = World.getInstance().getLocalSiegeNpcs(getSiegeLocationId());
		for (SiegeNpc npc : npcs) {
			if (npc != null && !npc.isDead())
				npc.getController().delete();
		}
	}

	private void initNpc(SiegeNpc target) throws SiegeException {
		switch (target.getRace()) {
			case GHENCHMAN_LIGHT:
				if (veille != null)
					throw new SiegeException("Tried to init veille twice!");
				veille = target;
				onWalkingEvent(veille, "600100000_npcpathgod_l");
				break;
			case GHENCHMAN_DARK:
				if (masta != null)
					throw new SiegeException("Tried to init masta twice!");
				masta = target;
				onWalkingEvent(masta, "600100000_npcpathgod_d");
				break;
			default:
				throw new SiegeException("Tried to init a npc with not supported TemplateType " + target.getNpcTemplateType() + " for agent fight!");
		}
	}

	private void registerListeners() {
		veille.getAggroList().addEventListener(veilleDoAddDamageListener);
		veille.getAi().addEventListener(veilleDeathListener);

		masta.getAggroList().addEventListener(mastaDoAddDamageListener);
		masta.getAi().addEventListener(mastaDeathListener);
	}

	private void removeListeners() {
		veille.getAggroList().removeEventListener(veilleDoAddDamageListener);
		veille.getAi().removeEventListener(veilleDeathListener);

		masta.getAggroList().removeEventListener(mastaDoAddDamageListener);
		masta.getAi().removeEventListener(mastaDeathListener);
	}

	public void setWinnerRace(SiegeRace race) {
		winner = race;
	}

	@Override
	public boolean isEndless() {
		return false;
	}

	@Override
	public void addAbyssPoints(Player player, int abysPoints) {
		getSiegeCounter().addAbyssPoints(player, abysPoints);
	}

}
