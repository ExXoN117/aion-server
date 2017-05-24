package com.aionemu.gameserver.spawnengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.ai.event.AIEventType;
import com.aionemu.gameserver.configs.main.CustomConfig;
import com.aionemu.gameserver.configs.main.SiegeConfig;
import com.aionemu.gameserver.controllers.GatherableController;
import com.aionemu.gameserver.controllers.NpcController;
import com.aionemu.gameserver.controllers.PetController;
import com.aionemu.gameserver.controllers.SiegeWeaponController;
import com.aionemu.gameserver.controllers.SummonController;
import com.aionemu.gameserver.controllers.effect.EffectController;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.geoEngine.collision.CollisionIntention;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Gatherable;
import com.aionemu.gameserver.model.gameobjects.GroupGate;
import com.aionemu.gameserver.model.gameobjects.Homing;
import com.aionemu.gameserver.model.gameobjects.Kisk;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.NpcObjectType;
import com.aionemu.gameserver.model.gameobjects.Pet;
import com.aionemu.gameserver.model.gameobjects.Servant;
import com.aionemu.gameserver.model.gameobjects.Summon;
import com.aionemu.gameserver.model.gameobjects.SummonedHouseNpc;
import com.aionemu.gameserver.model.gameobjects.Trap;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.PetCommonData;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.siege.SiegeNpc;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.house.House;
import com.aionemu.gameserver.model.rift.RiftLocation;
import com.aionemu.gameserver.model.skill.NpcSkillEntry;
import com.aionemu.gameserver.model.templates.VisibleObjectTemplate;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.model.templates.pet.PetTemplate;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.model.templates.spawns.basespawns.BaseSpawnTemplate;
import com.aionemu.gameserver.model.templates.spawns.riftspawns.RiftSpawnTemplate;
import com.aionemu.gameserver.model.templates.spawns.siegespawns.SiegeSpawnTemplate;
import com.aionemu.gameserver.model.templates.spawns.vortexspawns.VortexSpawnTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PLAYER_STATE;
import com.aionemu.gameserver.services.BaseService;
import com.aionemu.gameserver.services.RiftService;
import com.aionemu.gameserver.services.base.Base;
import com.aionemu.gameserver.skillengine.effect.SummonOwner;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.world.knownlist.CreatureAwareKnownList;
import com.aionemu.gameserver.world.knownlist.NpcKnownList;
import com.aionemu.gameserver.world.knownlist.PlayerAwareKnownList;

/**
 * @author ATracer
 */
public class VisibleObjectSpawner {

	private static final Logger log = LoggerFactory.getLogger(VisibleObjectSpawner.class);

	/**
	 * @param spawn
	 * @param instanceIndex
	 * @return
	 */
	protected static VisibleObject spawnNpc(SpawnTemplate spawn, int instanceIndex) {
		int npcId = spawn.getNpcId();
		NpcTemplate npcTemplate = DataManager.NPC_DATA.getNpcTemplate(npcId);
		if (npcTemplate == null) {
			log.error("No template for NPC " + String.valueOf(npcId));
			return null;
		}
		Npc npc = new Npc(IDFactory.getInstance().nextId(), new NpcController(), spawn, npcTemplate);
		npc.setCreatorId(spawn.getCreatorId());
		npc.setKnownlist(new NpcKnownList(npc));
		npc.setEffectController(new EffectController(npc));

		if (WalkerFormator.processClusteredNpc(npc, spawn.getWorldId(), instanceIndex))
			return npc;

		try {
			SpawnEngine.bringIntoWorld(npc, spawn, instanceIndex);
		} catch (Exception ex) {
			log.error("Error during spawn:", ex);
			npc.getController().delete();
		}
		return npc;
	}

	public static SummonedHouseNpc spawnHouseNpc(SpawnTemplate spawn, int instanceIndex, House creator, String masterName) {
		int npcId = spawn.getNpcId();
		NpcTemplate template = DataManager.NPC_DATA.getNpcTemplate(npcId);
		SummonedHouseNpc npc = new SummonedHouseNpc(IDFactory.getInstance().nextId(), new NpcController(), spawn, template, creator, masterName);
		npc.setKnownlist(new PlayerAwareKnownList(npc));
		npc.setEffectController(new EffectController(npc));
		SpawnEngine.bringIntoWorld(npc, spawn, instanceIndex);
		return npc;
	}

	protected static VisibleObject spawnBaseNpc(BaseSpawnTemplate spawn, int instanceIndex) {
		int npcId = spawn.getNpcId();
		NpcTemplate npcTemplate = DataManager.NPC_DATA.getNpcTemplate(npcId);

		if (npcTemplate == null) {
			log.error("No template for Base NPC " + String.valueOf(npcId));
			return null;
		}

		Base<?> base = BaseService.getInstance().getActiveBase(spawn.getId());
		if (base == null) // inactive base
			return null;

		if (spawn.getBaseRace() != base.getRace()) // avoid respawn of previous owner race spawns
			return null;

		Npc npc = new Npc(IDFactory.getInstance().nextId(), new NpcController(), spawn, npcTemplate);

		npc.setKnownlist(new NpcKnownList(npc));
		npc.setEffectController(new EffectController(npc));
		SpawnEngine.bringIntoWorld(npc, spawn, instanceIndex);

		return npc;
	}

	protected static VisibleObject spawnRiftNpc(RiftSpawnTemplate spawn, int instanceIndex) {
		if (!CustomConfig.RIFT_ENABLED) {
			return null;
		}

		int npcId = spawn.getNpcId();
		NpcTemplate npcTemplate = DataManager.NPC_DATA.getNpcTemplate(npcId);
		if (npcTemplate == null) {
			log.error("No template for NPC " + String.valueOf(npcId));
			return null;
		}
		IDFactory iDFactory = IDFactory.getInstance();
		Npc npc;

		int spawnId = spawn.getId();
		RiftLocation loc = RiftService.getInstance().getRiftLocation(spawnId);
		if (loc.isOpened() && spawnId == loc.getId()) {
			npc = new Npc(iDFactory.nextId(), new NpcController(), spawn, npcTemplate);
			npc.setKnownlist(new NpcKnownList(npc));
		} else {
			return null;
		}
		npc.setEffectController(new EffectController(npc));
		SpawnEngine.bringIntoWorld(npc, spawn, instanceIndex);
		return npc;
	}

	/**
	 * @param spawn
	 * @param instanceIndex
	 * @return
	 */
	protected static VisibleObject spawnSiegeNpc(SiegeSpawnTemplate spawn, int instanceIndex) {
		if (!SiegeConfig.SIEGE_ENABLED)
			return null;

		NpcTemplate npcTemplate = DataManager.NPC_DATA.getNpcTemplate(spawn.getNpcId());
		if (npcTemplate == null) {
			log.error("No template for NPC " + String.valueOf(spawn.getNpcId()));
			return null;
		}
		Npc npc = new SiegeNpc(IDFactory.getInstance().nextId(), new NpcController(), spawn, npcTemplate);
		npc.setKnownlist(new NpcKnownList(npc));
		npc.setEffectController(new EffectController(npc));
		SpawnEngine.bringIntoWorld(npc, spawn, instanceIndex);
		return npc;
	}

	protected static VisibleObject spawnInvasionNpc(VortexSpawnTemplate spawn, int instanceIndex) {
		if (!CustomConfig.VORTEX_ENABLED) {
			return null;
		}

		NpcTemplate npcTemplate = DataManager.NPC_DATA.getNpcTemplate(spawn.getNpcId());
		if (npcTemplate == null) {
			log.error("No template for NPC " + String.valueOf(spawn.getNpcId()));
			return null;
		}
		Npc npc = new Npc(IDFactory.getInstance().nextId(), new NpcController(), spawn, npcTemplate);
		npc.setKnownlist(new NpcKnownList(npc));
		npc.setEffectController(new EffectController(npc));
		SpawnEngine.bringIntoWorld(npc, spawn, instanceIndex);
		return npc;
	}

	/**
	 * @param spawn
	 * @param instanceIndex
	 * @return
	 */
	protected static VisibleObject spawnGatherable(SpawnTemplate spawn, int instanceIndex) {
		VisibleObjectTemplate template = DataManager.GATHERABLE_DATA.getGatherableTemplate(spawn.getNpcId());
		Gatherable gatherable = new Gatherable(spawn, template, IDFactory.getInstance().nextId(), new GatherableController());
		gatherable.setKnownlist(new PlayerAwareKnownList(gatherable));
		SpawnEngine.bringIntoWorld(gatherable, spawn, instanceIndex);
		return gatherable;
	}

	/**
	 * @param spawn
	 * @param instanceIndex
	 * @param creator
	 * @return
	 */
	public static Trap spawnTrap(SpawnTemplate spawn, int instanceIndex, Creature creator) {
		NpcTemplate npcTemplate = DataManager.NPC_DATA.getNpcTemplate(spawn.getNpcId());
		Trap trap = new Trap(IDFactory.getInstance().nextId(), new NpcController(), spawn, npcTemplate);
		trap.setKnownlist(new NpcKnownList(trap));
		trap.setEffectController(new EffectController(trap));
		trap.setCreator(creator);
		SpawnEngine.bringIntoWorld(trap, spawn, instanceIndex);
		PacketSendUtility.broadcastPacket(trap, new SM_PLAYER_STATE(trap));
		return trap;
	}

	/**
	 * @param spawn
	 * @param instanceIndex
	 * @param creator
	 * @return
	 */
	public static GroupGate spawnGroupGate(SpawnTemplate spawn, int instanceIndex, Creature creator) {
		NpcTemplate npcTemplate = DataManager.NPC_DATA.getNpcTemplate(spawn.getNpcId());
		GroupGate groupgate = new GroupGate(IDFactory.getInstance().nextId(), new NpcController(), spawn, npcTemplate);
		groupgate.setKnownlist(new PlayerAwareKnownList(groupgate));
		groupgate.setEffectController(new EffectController(groupgate));
		groupgate.setCreator(creator);
		SpawnEngine.bringIntoWorld(groupgate, spawn, instanceIndex);
		return groupgate;
	}

	/**
	 * @param spawn
	 * @param instanceIndex
	 * @param creator
	 * @return
	 */
	public static Kisk spawnKisk(SpawnTemplate spawn, int instanceIndex, Player creator) {
		NpcTemplate template = DataManager.NPC_DATA.getNpcTemplate(spawn.getNpcId());
		Kisk kisk = new Kisk(IDFactory.getInstance().nextId(), new NpcController(), spawn, template, creator);
		kisk.setMasterName(creator.getName());
		kisk.setKnownlist(new PlayerAwareKnownList(kisk));
		kisk.setEffectController(new EffectController(kisk));
		SpawnEngine.bringIntoWorld(kisk, spawn, instanceIndex);
		return kisk;
	}

	/**
	 * @param owner
	 * @author ViAl Spawns postman for express mail
	 */
	public static Npc spawnPostman(final Player owner) {
		int npcId = owner.getRace() == Race.ELYOS ? 798100 : 798101;
		NpcTemplate template = DataManager.NPC_DATA.getNpcTemplate(npcId);
		double radian = Math.toRadians(PositionUtil.convertHeadingToAngle(owner.getHeading()));
		Vector3f pos = GeoService.getInstance().getClosestCollision(owner, owner.getX() + (float) (Math.cos(radian) * 7),
			owner.getY() + (float) (Math.sin(radian) * 7), owner.getZ(), false, CollisionIntention.PHYSICAL.getId());
		SpawnTemplate spawn = SpawnEngine.newSingleTimeSpawn(owner.getWorldId(), npcId, pos.getX(), pos.getY(), pos.getZ(), (byte) 0);
		final Npc postman = new Npc(IDFactory.getInstance().nextId(), new NpcController(), spawn, template);
		postman.setCreatorId(owner.getObjectId());
		postman.setMasterName(owner.getName());
		postman.setKnownlist(new PlayerAwareKnownList(postman));
		postman.setEffectController(new EffectController(postman));
		SpawnEngine.bringIntoWorld(postman, spawn, owner.getInstanceId());
		owner.setPostman(postman);
		return postman;
	}

	public static Npc spawnFunctionalNpc(final Player owner, int npcId, SummonOwner summonOwner) {
		NpcTemplate template = DataManager.NPC_DATA.getNpcTemplate(npcId);
		double radian = Math.toRadians(PositionUtil.convertHeadingToAngle(owner.getHeading()));
		Vector3f pos = GeoService.getInstance().getClosestCollision(owner, owner.getX() + (float) (Math.cos(radian) * 1),
			owner.getY() + (float) (Math.sin(radian) * 1), owner.getZ(), false, CollisionIntention.PHYSICAL.getId());
		SpawnTemplate spawn = SpawnEngine.newSingleTimeSpawn(owner.getWorldId(), npcId, pos.getX(), pos.getY(), pos.getZ(), (byte) 0);
		final Npc functionalNpc = new Npc(IDFactory.getInstance().nextId(), new NpcController(), spawn, template);
		functionalNpc.setKnownlist(new PlayerAwareKnownList(functionalNpc));
		functionalNpc.setEffectController(new EffectController(functionalNpc));
		functionalNpc.getAi().onCustomEvent(1, owner, summonOwner);
		SpawnEngine.bringIntoWorld(functionalNpc, spawn, owner.getInstanceId());
		return functionalNpc;
	}

	/**
	 * @param spawn
	 * @param instanceIndex
	 * @param creator
	 * @param skillId
	 * @param level
	 * @return
	 */
	public static Servant spawnServant(SpawnTemplate spawn, int instanceIndex, Creature creator, int level, NpcObjectType objectType) {
		NpcTemplate npcTemplate = DataManager.NPC_DATA.getNpcTemplate(spawn.getNpcId());
		byte servantLevel = creator.getLevel();
		Servant servant = new Servant(IDFactory.getInstance().nextId(), new NpcController(), spawn, npcTemplate, servantLevel);
		servant.setKnownlist(new NpcKnownList(servant));
		servant.setEffectController(new EffectController(servant));
		servant.setCreator(creator);
		servant.setNpcObjectType(objectType);
		servant.setUpStats();
		SpawnEngine.bringIntoWorld(servant, spawn, instanceIndex);
		if (servant.getSkillList() != null) {
			NpcSkillEntry skill = servant.getSkillList().getRandomSkill();
			if (skill != null) {
				SkillTemplate st = DataManager.SKILL_DATA.getSkillTemplate(skill.getSkillId());
				if (st.getStartconditions() != null && st.getHpCondition() != null) {
					int hp = (st.getHpCondition().getHpValue() * 3);
					servant.getLifeStats().setCurrentHp(hp);
				}
			}
		}
		return servant;
	}

	/**
	 * @param spawn
	 * @param instanceIndex
	 * @param creator
	 * @param skillId
	 * @param level
	 * @return
	 */
	public static Servant spawnEnemyServant(SpawnTemplate spawn, int instanceIndex, Creature creator, byte servantLvl) {
		NpcTemplate npcTemplate = DataManager.NPC_DATA.getNpcTemplate(spawn.getNpcId());
		Servant servant = new Servant(IDFactory.getInstance().nextId(), new NpcController(), spawn, npcTemplate, servantLvl);
		servant.setKnownlist(new NpcKnownList(servant));
		servant.setEffectController(new EffectController(servant));
		servant.setCreator(creator);
		servant.setNpcObjectType(NpcObjectType.SERVANT);
		SpawnEngine.bringIntoWorld(servant, spawn, instanceIndex);
		return servant;
	}

	/**
	 * @param spawn
	 * @param instanceIndex
	 * @param creator
	 * @param attackCount
	 * @return
	 */
	public static Homing spawnHoming(SpawnTemplate spawn, int instanceIndex, Creature creator, int attackCount, int skillId) {
		NpcTemplate npcTemplate = DataManager.NPC_DATA.getNpcTemplate(spawn.getNpcId());
		int creatureLevel = creator.getLevel();
		Homing homing = new Homing(IDFactory.getInstance().nextId(), new NpcController(), spawn, npcTemplate, (byte) creatureLevel, skillId);
		homing.setState(CreatureState.WEAPON_EQUIPPED);
		homing.setKnownlist(new NpcKnownList(homing));
		homing.setEffectController(new EffectController(homing));
		homing.setCreator(creator);
		homing.setAttackCount(attackCount);
		SpawnEngine.bringIntoWorld(homing, spawn, instanceIndex);
		return homing;
	}

	public static Summon spawnSummon(Player creator, int npcId, int skillId, int time) {
		double radian = Math.toRadians(PositionUtil.convertHeadingToAngle(creator.getHeading()));
		float x = creator.getX() + (float) (Math.cos(radian) * 2);
		float y = creator.getY() + (float) (Math.sin(radian) * 2);
		float z = creator.getZ();
		byte heading = creator.getHeading();
		int worldId = creator.getWorldId();
		int instanceId = creator.getInstanceId();

		SpawnTemplate spawn = SpawnEngine.newSingleTimeSpawn(worldId, npcId, x, y, z, heading);
		NpcTemplate npcTemplate = DataManager.NPC_DATA.getNpcTemplate(npcId);

		boolean isSiegeWeapon = npcTemplate.getAi().equals("siege_weapon");
		Summon summon = new Summon(IDFactory.getInstance().nextId(), isSiegeWeapon ? new SiegeWeaponController(npcId) : new SummonController(), spawn,
			npcTemplate, time);
		summon.setKnownlist(new CreatureAwareKnownList(summon));
		summon.setEffectController(new EffectController(summon));
		summon.setMaster(creator);
		summon.getLifeStats().synchronizeWithMaxStats();
		summon.setSummonedBySkillId(skillId);

		SpawnEngine.bringIntoWorld(summon, spawn, instanceId);
		if (isSiegeWeapon)
			summon.getAi().onGeneralEvent(AIEventType.SPAWNED);
		return summon;
	}

	/**
	 * @param player
	 * @param petId
	 * @return
	 */
	public static Pet spawnPet(Player player, int petId) {
		PetCommonData petCommonData = player.getPetList().getPet(petId);
		if (petCommonData == null)
			return null;

		PetTemplate petTemplate = DataManager.PET_DATA.getPetTemplate(petId);
		if (petTemplate == null)
			return null;

		Pet pet = new Pet(petTemplate, new PetController(), petCommonData, player);
		pet.setKnownlist(new PlayerAwareKnownList(pet));

		float x = player.getX();
		float y = player.getY();
		float z = player.getZ();
		byte heading = player.getHeading();
		int worldId = player.getWorldId();
		int instanceId = player.getInstanceId();
		SpawnTemplate spawn = SpawnEngine.newSingleTimeSpawn(worldId, petId, x, y, z, heading);
		SpawnEngine.bringIntoWorld(pet, spawn, instanceId);
		player.setPet(pet);
		return pet;
	}

}
