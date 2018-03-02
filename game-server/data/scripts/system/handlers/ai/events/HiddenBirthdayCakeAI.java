package ai.events;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemAddType;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemUpdateType;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.services.item.ItemService.ItemUpdatePredicate;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.ActionItemNpcAI;

/**
 * Created on 11th November, 2017.<br>
 * <br>
 * TODO: Remove drop registration if global drops or something like that will ever
 * be able to support variable quantities.
 * 
 * @author Estrayl
 */
@AIName("hidden_cake")
public class HiddenBirthdayCakeAI extends ActionItemNpcAI {

	private final static int JEST_SPAWN_CHANCE = 25;
	private final static int[] JEST_SPAWN_IDS = { 210341, 214732, 210595 };

	public HiddenBirthdayCakeAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleUseItemFinish(Player player) {
		if (getOwner().isInState(CreatureState.DEAD))
			return;

		int droppedItemId = getDroppedItemId(player.getRace());

		if (Rnd.chance() < JEST_SPAWN_CHANCE) {
			VisibleObject s = spawn(Rnd.get(JEST_SPAWN_IDS), getOwner().getX(), getOwner().getY(), getOwner().getZ(), (byte) 0);
			ThreadPoolManager.getInstance().schedule(() -> s.getController().delete(), 120000);
			PacketSendUtility.sendPacket(player,
				SM_SYSTEM_MESSAGE.STR_MSG_TOYPET_FEED_FOOD_NOT_LOVEFLAVOR(s.getObjectTemplate().getL10n(), getObjectTemplate().getL10n()));
		}
		int itemCount = getItemCount(droppedItemId);
		String item = itemCount + " " + DataManager.ITEM_DATA.getItemTemplate(droppedItemId).getL10n();
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_HOUSING_OBJECT_REWARD_ITEM(getObjectTemplate().getL10n(), item));
		ItemService.addItem(player, droppedItemId, itemCount, true, new ItemUpdatePredicate(ItemAddType.SERVER_GENERATED, ItemUpdateType.STATS_CHANGE));
		AIActions.die(this, player);
		super.handleUseItemFinish(player);
	}

	private int getDroppedItemId(Race playerRace) {
		List<Integer> items = new ArrayList<>();
		int rnd = Rnd.get(1000);
		if (rnd < 49) {
			items.addAll(Arrays.asList(169610093, 169610140, 168310019, 187060161, 169600005, 188054238, 188053007));
		} else if (rnd < 299) {
			items.addAll(
				Arrays.asList(188052638, 110900673, 110900765, 110900781, 190100077, 190100095, 190100083, 190070016, 190070018, 188053006, 188053618));
		} else {
			items.addAll(
				Arrays.asList(162000137, 162000139, 162000141, 186000399, 160010217, 186000236, 188053295, 166500002, 166020000, 166030007, 186000242));
			items.add(playerRace == Race.ELYOS ? 184002016 : 184002017);
		}
		return Rnd.get(items);
	}

	private int getItemCount(int itemId) {
		switch (itemId) {
			case 162000141: // Sublime Wind Serum
			case 166030007: // [Event] Tempering Solution
			case 166500002: // Amplification Stone
			case 186000242: // Ceramium Medal
				return 3;
			case 160010217: // [Event] Birthday Cake Piece
			case 166020000: // Omega Enchantment Stone
			case 162000137: // Sublime Life Serum
			case 162000139: // Sublime Mana Serum
				return 5;
			case 186000399: // Honorable Conqueror's Mark
				return 15;
			case 186000236: // Blood Mark
				return 30;
			case 184002016: // [Event] Birthday Cake Kisk
			case 184002017: // [Event] Birthday Cake Kisk
				return 2;
			default:
				return 1;
		}
	}

	@Override
	public boolean ask(AIQuestion question) {
		switch (question) {
			case SHOULD_DECAY:
			case SHOULD_LOOT:
				return false;
			default:
				return super.ask(question);
		}
	}

}