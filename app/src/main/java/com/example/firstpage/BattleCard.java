package com.example.firstpage;

public class BattleCard {
    private CardType type;
    private int effectValue;   // For beneficial cards, positive; for damaging cards, positive (effect applied accordingly)
    private String fact;       // Description of the card.
    private int imageResId;    // Drawable resource ID for the card's front image.

    public BattleCard(CardType type, int effectValue, String fact, int imageResId) {
        this.type = type;
        this.effectValue = effectValue;
        this.fact = fact;
        this.imageResId = imageResId;
    }

    public CardType getType() {
        return type;
    }

    public int getEffectValue() {
        return effectValue;
    }

    public String getFact() {
        return fact;
    }

    public int getImageResId() {
        return imageResId;
    }
}