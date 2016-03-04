package com.aionemu.gameserver.skillengine.effect;

/**
 * @author ATracer
 */
public enum AbnormalState {
	BUFF(0),
	POISON(1 << 0),
	BLEED(1 << 1),
	PARALYZE(1 << 2),
	SLEEP(1 << 3),
	ROOT(1 << 4),
	BLIND(1 << 5),
	CHARM(1 << 6),
	DISEASE(1 << 7),
	SILENCE(1 << 8),
	FEAR(1 << 9),
	CURSE(1 << 10),
	CONFUSE(1 << 11),
	STUN(1 << 12),
	PETRIFICATION(1 << 13),
	STUMBLE(1 << 14),
	STAGGER(1 << 15),
	OPENAERIAL(1 << 16),
	SNARE(1 << 17),
	SLOW(1 << 18),
	SPIN(1 << 19),
	BIND(1 << 20),
	DEFORM(1 << 21),
	PULLED(1 << 22),
	NOFLY(1 << 23),
	KNOCKBACK(1 << 24),
	UNK_2(1 << 25),
	UNK_3(1 << 26),
	UNK_4(1 << 27),
	UNK_5(1 << 28),
	HIDE(1 << 29),
	UNK_6(1 << 30),
	SANCTUARY(1 << 31),

	/**
	 * Compound abnormal states
	 */
	CANT_ATTACK_STATE(SPIN.id | SLEEP.id | STUN.id | STUMBLE.id | STAGGER.id | OPENAERIAL.id | PARALYZE.id | FEAR.id | PULLED.id | SANCTUARY.id | CONFUSE.id),
	STANCE_OFF(SPIN.id | STUN.id | STUMBLE.id | STAGGER.id | OPENAERIAL.id | PARALYZE.id | FEAR.id | PULLED.id | SANCTUARY.id),
	CANT_MOVE_STATE(SPIN.id | ROOT.id | SLEEP.id | STUMBLE.id | STUN.id | STAGGER.id | OPENAERIAL.id | PARALYZE.id | PULLED.id | SANCTUARY.id),
	DISMOUT_RIDE(SPIN.id | ROOT.id | SLEEP.id | STUMBLE.id | STUN.id | STAGGER.id | OPENAERIAL.id | PARALYZE.id | PULLED.id | FEAR.id | SNARE.id),
	AUTOMATICALLY_STANDUP(PARALYZE.id | SLEEP.id | FEAR.id | STUN.id | STAGGER.id | OPENAERIAL.id | SPIN.id | DEFORM.id | PULLED.id),
	ANY_STUN(SPIN.getId() | STUN.getId() | STUMBLE.getId() | STAGGER.getId());

	private int id;

	private AbnormalState(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public static AbnormalState getIdByName(String name) {
		for (AbnormalState id : values()) {
			if (id.name().equals(name))
				return id;
		}
		return null;
	}

	public static AbnormalState getStateById(int id) {
		for (AbnormalState as : values()) {
			if (as.getId() == id)
				return as;
		}
		return null;
	}
}
