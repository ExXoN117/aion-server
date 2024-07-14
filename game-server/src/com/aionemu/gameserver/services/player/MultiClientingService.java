package com.aionemu.gameserver.services.player;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.SecurityConfig;
import com.aionemu.gameserver.configs.main.SecurityConfig.MultiClientingRestrictionMode;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.world.World;

public class MultiClientingService {

	private static final Logger log = LoggerFactory.getLogger(MultiClientingService.class);
	private static final Map<Integer, AccountSession> sessionsByAccountId = new ConcurrentHashMap<>();

	public static boolean tryEnterWorld(Player player, AionConnection con) {
		if (SecurityConfig.MULTI_CLIENTING_RESTRICTION_MODE == MultiClientingRestrictionMode.FULL) {
			String mac = con.getMacAddress();
			String hdd = con.getHddSerial();
			String ip = con.getIP();
			for (Player onlinePlayer : World.getInstance().getAllPlayers()) {
				boolean sameIp = ip.equals(onlinePlayer.getClientConnection().getIP());
				boolean sameMac = !mac.isEmpty() && mac.equals(onlinePlayer.getClientConnection().getMacAddress());
				boolean sameHdd = !hdd.isEmpty() && hdd.equals(onlinePlayer.getClientConnection().getHddSerial());
				if (sameIp && (sameMac || sameHdd)) {
					log.info("Blocked {} from logging on (multi-clienting on {} with {})", player, sameMac ? "MAC address " + mac : "HDD " + hdd, onlinePlayer);
					return false;
				}
			}
		} else if (SecurityConfig.MULTI_CLIENTING_RESTRICTION_MODE == MultiClientingRestrictionMode.SAME_FACTION) {
			sessionsByAccountId.values().removeIf(AccountSession::isExpired);
			synchronized (sessionsByAccountId) {
				Integer matchedAccountId = checkForFactionSwitchCooldownTime(player.getRace(), con);
				if (matchedAccountId != null) {
					log.info("Blocked {} from logging on (faction switch cooldown from account ID {})", player, matchedAccountId);
					return false;
				}
				AccountSession accountSession = sessionsByAccountId.computeIfAbsent(player.getAccount().getId(), AccountSession::new);
				accountSession.putIdentifiers(con);
				accountSession.enterWorld(player);
			}
		}
		return true;
	}

	public static void onLeaveWorld(Player player) {
		AccountSession session = sessionsByAccountId.get(player.getAccount().getId());
		if (session != null)
			session.leaveWorld(player);
	}

	public static Integer checkForFactionSwitchCooldownTime(Race race, AionConnection con) {
		Race oppositeRace = race == Race.ELYOS ? Race.ASMODIANS : Race.ELYOS;
		long minLastOnlineMillis = System.currentTimeMillis() - Duration.ofMinutes(SecurityConfig.MULTI_CLIENTING_FACTION_SWITCH_COOLDOWN_MINUTES).toMillis();
		return sessionsByAccountId.values().stream()
			.filter(s -> s.wasPlayingOnSameIpMacOrHdd(oppositeRace, minLastOnlineMillis, con))
			.findAny()
			.map(s -> s.accountId)
			.orElse(null);
	}

	private static class AccountSession {

		private final int accountId;
		private final Map<Race, Long> lastCharOnlineTimeMillis = new ConcurrentHashMap<>();
		private final List<Identifiers> identifiers = new LinkedList<>();

		public AccountSession(int accountId) {
			this.accountId = accountId;
		}

		synchronized void putIdentifiers(AionConnection connection) {
			Identifiers ids = new Identifiers(connection.getIP(), connection.getMacAddress(), connection.getHddSerial());
			if (!identifiers.contains(ids)) {
				identifiers.addFirst(ids);
				while (identifiers.size() > 3)
					identifiers.removeLast();
			}
		}

		synchronized boolean hasAny(String ip, String mac, String hdd) {
			return identifiers.stream().anyMatch(identifiers -> identifiers.ip.equals(ip) || identifiers.mac.equals(mac) || !hdd.isEmpty() && identifiers.hdd.equals(hdd));
		}

		boolean isExpired() {
			long minLastOnline = System.currentTimeMillis() - Duration.ofMinutes(SecurityConfig.MULTI_CLIENTING_FACTION_SWITCH_COOLDOWN_MINUTES).toMillis();
			return lastCharOnlineTimeMillis.values().stream().noneMatch(t -> t > minLastOnline);
		}

		void enterWorld(Player player) {
			lastCharOnlineTimeMillis.put(player.getRace(), Long.MAX_VALUE);
		}

		void leaveWorld(Player player) {
			lastCharOnlineTimeMillis.put(player.getRace(), System.currentTimeMillis());
		}

		boolean wasPlayingOnSameIpMacOrHdd(Race race, long minLastOnlineMillis, AionConnection con) {
			Long lastOnlineMillis = lastCharOnlineTimeMillis.get(race);
			return lastOnlineMillis != null && lastOnlineMillis > minLastOnlineMillis && hasAny(con.getIP(), con.getMacAddress(), con.getHddSerial());
		}
	}

	private record Identifiers(String ip, String mac, String hdd) {}
}
