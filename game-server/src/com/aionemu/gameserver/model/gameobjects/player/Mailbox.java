package com.aionemu.gameserver.model.gameobjects.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.model.gameobjects.Letter;
import com.aionemu.gameserver.model.gameobjects.LetterType;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MAIL_SERVICE;
import com.aionemu.gameserver.services.mail.MailService;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author kosyachok
 * @modified Atracer
 */
public class Mailbox {

	private Map<Integer, Letter> mails = new ConcurrentHashMap<>();
	private Map<Integer, Letter> reserveMail = new ConcurrentHashMap<>();
	private Player owner;

	// 0x00 - closed
	// 0x01 - regular
	// 0x02 - express
	public byte mailBoxState = 0;

	public Mailbox(Player player) {
		this.owner = player;
	}

	/**
	 * @param letter
	 */
	public void putLetterToMailbox(Letter letter) {
		if (haveFreeSlots())
			mails.put(letter.getObjectId(), letter);
		else
			reserveMail.put(letter.getObjectId(), letter);
	}

	/**
	 * Get all letters in mailbox (sorted according to time received)
	 * 
	 * @return
	 */
	public Collection<Letter> getLetters() {
		SortedSet<Letter> letters = new TreeSet<>(new Comparator<Letter>() {

			@Override
			public int compare(Letter o1, Letter o2) {
				if (o1.getTimeStamp().getTime() > o2.getTimeStamp().getTime())
					return 1;
				if (o1.getTimeStamp().getTime() < o2.getTimeStamp().getTime())
					return -1;

				return o1.getObjectId() > o2.getObjectId() ? 1 : -1;
			}

		});

		for (Letter letter : mails.values()) {
			letters.add(letter);
		}
		return letters;
	}

	/**
	 * Get system letters which senders start with the string specified and were received since the last player login
	 * 
	 * @param substring
	 *          must start with special characters: % or $$
	 * @return new list of letters
	 */
	public List<Letter> getNewSystemLetters(String substring) {
		List<Letter> letters = new ArrayList<>();
		if (substring.startsWith("%") || substring.startsWith("$$")) {
			long lastOnlineMillis = owner.getCommonData().getLastOnline() == null ? 0 : owner.getCommonData().getLastOnline().getTime();
			for (Letter letter : mails.values()) {
				if (!letter.isUnread() || letter.getSenderName() == null || !letter.getSenderName().startsWith(substring))
					continue;
				if (lastOnlineMillis > letter.getTimeStamp().getTime())
					continue;
				letters.add(letter);
			}
		}
		return letters;
	}

	/**
	 * Get letter with specified letter id
	 * 
	 * @param letterObjId
	 * @return
	 */
	public Letter getLetterFromMailbox(int letterObjId) {
		return mails.get(letterObjId);
	}

	/**
	 * Check whether mailbox contains empty letters
	 * 
	 * @return
	 */
	public boolean haveUnread() {
		for (Letter letter : mails.values()) {
			if (letter.isUnread())
				return true;
		}
		return false;
	}

	public final int getUnreadCount() {
		int unreadCount = 0;
		for (Letter letter : mails.values()) {
			if (letter.isUnread())
				unreadCount++;
		}
		return unreadCount;
	}

	public boolean haveUnreadByType(LetterType letterType) {
		for (Letter letter : mails.values()) {
			if (letter.isUnread() && letter.getLetterType() == letterType)
				return true;
		}
		return false;
	}

	public final int getUnreadCountByType(LetterType letterType) {
		int count = 0;
		for (Letter letter : mails.values()) {
			if (letter.isUnread() && letter.getLetterType() == letterType)
				count++;
		}
		return count;
	}

	public boolean haveFreeSlots() {
		return mails.size() < 100;
	}

	public void removeLetter(int letterId) {
		mails.remove(letterId);
		uploadReserveLetters();
		owner.getCommonData().setMailboxLetters(size());
	}

	/**
	 * Current size of mailbox (including those in overflow storage)
	 */
	public int size() {
		return mails.size() + reserveMail.size();
	}

	public void uploadReserveLetters() {
		if (reserveMail.size() > 0 && haveFreeSlots()) {
			for (Letter letter : reserveMail.values()) {
				if (haveFreeSlots()) {
					mails.put(letter.getObjectId(), letter);
					reserveMail.remove(letter.getObjectId());
				} else
					break;
			}
			MailService.refreshMail(getOwner());
		}
	}

	public void sendMailList(boolean expressOnly) {
		PacketSendUtility.sendPacket(owner, new SM_MAIL_SERVICE(owner, getLetters(), expressOnly));
	}

	public Player getOwner() {
		return owner;
	}

}
