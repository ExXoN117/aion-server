package com.aionemu.gameserver.dao;

import com.aionemu.commons.database.dao.DAO;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * @author Estrayl
 */
public abstract class BonusPackDAO implements DAO {

	public abstract int loadReceivingPlayer(final Player player);

	public abstract void storePlayer(final Player player, final int receivingPlayer);

	@Override
	public String getClassName() {
		return BonusPackDAO.class.getName();
	}
}
