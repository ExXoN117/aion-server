package com.aionemu.gameserver.utils.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.administration.AdminConfig;
import com.aionemu.gameserver.configs.main.LoggingConfig;
import com.aionemu.gameserver.configs.main.PunishmentConfig;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author MrPoke
 * @modified Neon
 */
public class AuditLogger {

	private static final Logger log = LoggerFactory.getLogger("AUDIT_LOG");

	/**
	 * Logs message, if audit log is enabled.<br>
	 * Notifies permitted online staff members.<br>
	 * Automatically punishes player, if punishments are enabled.
	 */
	public static final void info(Player player, String message) {
		if (PunishmentConfig.PUNISHMENT_ENABLE)
			AutoBan.punishment(player, message);

		message = player + " " + message;

		if (LoggingConfig.LOG_AUDIT)
			log.info(message);

		for (Player gm : GMService.getInstance().getOnlineStaffMembers()) {
			if (gm.hasAccess(AdminConfig.AUDIT_INFO))
				PacketSendUtility.sendMessage(gm, message, ChatType.YELLOW);
		}
	}
}
