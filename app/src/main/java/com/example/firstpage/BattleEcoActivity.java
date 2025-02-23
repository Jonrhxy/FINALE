package com.example.firstpage;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BattleEcoActivity extends AppCompatActivity {

    private TextView playerHealthText, computerHealthText, roundCounterText, battleLogText;
    private TextView playerShieldText, computerShieldText, playerEnergyText, computerEnergyText;

    private ImageView aiDeckPreview, aiDrawnCard, aiCharacterImage;
    private ImageView playerDeckPreview, playerDrawnCard, playerCharacterImage;

    private LinearLayout handLayout;

    private Button playerDrawButton;

    private int playerHealth = 100;
    private int computerHealth;
    private int playerShield = 0, computerShield = 0;
    private int playerEnergy = 3, computerEnergy = 3;
    private final int MAX_ROUNDS = 25;
    private int currentRound = 1;

    private Player player, computer;
    private List<BattleCard> playerHand;
    private Handler handler = new Handler();
    private MediaPlayer backgroundMusic;

    private int currentBossIndex = 0;
    private final int[] BOSS_HEALTHS = {100, 120, 150};
    private final int[] BOSS_BACKGROUNDS = {
            R.drawable.background1,
            R.drawable.background2,
            R.drawable.background3
    };
    private final int[] BOSS_IMAGES = {
            R.drawable.burger_boss,
            R.drawable.cake_boss,
            R.drawable.taco_boss
    };

    private boolean playerAttacksFirst;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ***** Force fullscreen (remove status bar) *****
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        // ***** End fullscreen code *****

        setContentView(R.layout.activity_battle_eco);

        // Bind UI components.
        playerHealthText = findViewById(R.id.playerHealthText);
        computerHealthText = findViewById(R.id.computerHealthText);
        roundCounterText = findViewById(R.id.roundCounterText);
        battleLogText = findViewById(R.id.battleLogText);
        playerShieldText = findViewById(R.id.playerShieldText);
        computerShieldText = findViewById(R.id.computerShieldText);
        playerEnergyText = findViewById(R.id.playerEnergyText);
        computerEnergyText = findViewById(R.id.computerEnergyText);

        aiDeckPreview = findViewById(R.id.aiDeckPreview);
        aiDrawnCard = findViewById(R.id.aiDrawnCard);
        aiCharacterImage = findViewById(R.id.aiCharacterImage);

        playerDeckPreview = findViewById(R.id.playerDeckPreview);
        playerDrawnCard = findViewById(R.id.playerDrawnCard);
        playerCharacterImage = findViewById(R.id.playerCharacterImage);

        handLayout = findViewById(R.id.handLayout);
        playerDrawButton = findViewById(R.id.playerDrawButton);

        handLayout.setVisibility(View.GONE);

        backgroundMusic = MediaPlayer.create(this, R.raw.music_cardgame);
        backgroundMusic.setLooping(true);
        backgroundMusic.start();

        player = new Player("Player", 100);
        computer = new Player("Computer", BOSS_HEALTHS[currentBossIndex]);
        computerHealth = BOSS_HEALTHS[currentBossIndex];

        aiCharacterImage.setImageResource(BOSS_IMAGES[currentBossIndex]);
        findViewById(R.id.battleEcoRoot).setBackgroundResource(BOSS_BACKGROUNDS[currentBossIndex]);

        playerHand = new ArrayList<>();

        // Add cards to the decks.
        player.addCard(new BattleCard(CardType.HEAL, 25, "Heal (+25 HP)", R.drawable.banana_cards));
        player.addCard(new BattleCard(CardType.ENERGY, 1, "Energy (+1 Energy)", R.drawable.almond_cards));
        player.addCard(new BattleCard(CardType.SHIELD, 40, "Shield (+40 Shields)", R.drawable.peas_cards));
        player.addCard(new BattleCard(CardType.POISON, 15, "Poison (3 DPS for 5 sec)", R.drawable.meat_cards));
        player.addCard(new BattleCard(CardType.SLASH, 75, "Slash (-75 Burst Damage)", R.drawable.cheese_cards));
        player.addCard(new BattleCard(CardType.PIERCING, 50, "Piercing (-50 Damage, Ignores Shields)", R.drawable.shrimp_cards));
        player.addCard(new BattleCard(CardType.NORMAL_ATTACK, 20, "Normal Attack (-20 Damage, No Energy Cost)", R.drawable.artificialgrowth_cards));

        computer.addCard(new BattleCard(CardType.HEAL, 25, "Heal (+25 HP)", R.drawable.banana_cards));
        computer.addCard(new BattleCard(CardType.ENERGY, 1, "Energy (+1 Energy)", R.drawable.almond_cards));
        computer.addCard(new BattleCard(CardType.SHIELD, 40, "Shield (+40 Shields)", R.drawable.peas_cards));
        computer.addCard(new BattleCard(CardType.POISON, 15, "Poison (3 DPS for 5 sec)", R.drawable.meat_cards));
        computer.addCard(new BattleCard(CardType.SLASH, 75, "Slash (-75 Burst Damage)", R.drawable.cheese_cards));
        computer.addCard(new BattleCard(CardType.PIERCING, 50, "Piercing (-50 Damage, Ignores Shields)", R.drawable.shrimp_cards));
        computer.addCard(new BattleCard(CardType.NORMAL_ATTACK, 20, "Normal Attack (-20 Damage, No Energy Cost)", R.drawable.artificialgrowth_cards));

        updateDeckPreview(player, playerDeckPreview);
        updateDeckPreview(computer, aiDeckPreview);

        updateUI();
        updateRoundCounter();

        showInstructionDialogue(new Runnable() {
            @Override
            public void run() {
                rollForFirstAttacker();
            }
        });

        playerDrawButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (playerHealth > 0 && computerHealth > 0) {
                    drawInitialHand();
                    showHandSelection();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (backgroundMusic != null && backgroundMusic.isPlaying()) {
            backgroundMusic.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (backgroundMusic != null && !backgroundMusic.isPlaying()) {
            backgroundMusic.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundMusic != null) {
            backgroundMusic.stop();
            backgroundMusic.release();
            backgroundMusic = null;
        }
    }

    // Show instruction dialogue via NPC.
    private void showInstructionDialogue(final Runnable onCloseAction) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_npc_explanation, null);
        ImageView npcImage = dialogView.findViewById(R.id.npcImage);
        TextView npcMessage = dialogView.findViewById(R.id.npcMessage);
        Button npcCloseButton = dialogView.findViewById(R.id.npcCloseButton);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.TransparentFullScreenDialog);
        builder.setView(dialogView);
        final AlertDialog npcDialog = builder.create();
        npcDialog.setCanceledOnTouchOutside(false);
        npcDialog.setOnShowListener(dialogInterface -> {
            if (npcDialog.getWindow() != null) {
                npcDialog.getWindow().setLayout(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT
                );
            }
        });

        playerDrawButton.setEnabled(false);

        animateText(npcMessage,
                "Instructions: Use your cards wisely. Red cards cost 1 energy, ENERGY cards add energy, " +
                        "HEAL restores HP, and SHIELD protects you. Tap 'Draw Card' to begin the battle.",
                0
        );

        npcCloseButton.setOnClickListener(v -> {
            npcDialog.dismiss();
            if (onCloseAction != null) {
                onCloseAction.run();
            }
        });
        npcDialog.show();
    }

    private void rollForFirstAttacker() {
        playerDrawButton.setEnabled(false);

        final int rollDuration = 2000;
        final int rollInterval = 200;
        final Handler rollHandler = new Handler();
        final long startTime = System.currentTimeMillis();

        rollHandler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < rollDuration) {
                    if (new Random().nextBoolean()) {
                        battleLogText.setText("Rolling... Player will attack first");
                    } else {
                        battleLogText.setText("Rolling... AI will attack first");
                    }
                    rollHandler.postDelayed(this, rollInterval);
                } else {
                    if (new Random().nextBoolean()) {
                        battleLogText.setText("Final result: Player will attack first");
                        playerAttacksFirst = true;
                    } else {
                        battleLogText.setText("Final result: AI will attack first");
                        playerAttacksFirst = false;
                    }
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showNpcDialogue(
                                    "Intro: The forest is under siege! The Burger Boss, Cake Boss, and Taco Boss " +
                                            "have invaded our woodland home, unleashing high carbon chaos and polluting " +
                                            "the skies. Only the noble Plant Heroes, champions of low carbon and " +
                                            "sustainability, can restore balance and save the ecosystem.",
                                    false
                            );
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (playerAttacksFirst) {
                                        playerDrawButton.setEnabled(true);
                                    } else {
                                        processComputerTurn();
                                    }
                                }
                            }, 2000);
                        }
                    }, 1000);
                }
            }
        });
    }

    private void showNpcDialogue(String message, boolean enableAfterDismiss) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_npc_explanation, null);
        ImageView npcImage = dialogView.findViewById(R.id.npcImage);
        TextView npcMessage = dialogView.findViewById(R.id.npcMessage);
        Button npcCloseButton = dialogView.findViewById(R.id.npcCloseButton);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.TransparentFullScreenDialog);
        builder.setView(dialogView);
        final AlertDialog npcDialog = builder.create();
        npcDialog.setCanceledOnTouchOutside(false);
        npcDialog.setOnShowListener(dialogInterface -> {
            if (npcDialog.getWindow() != null) {
                npcDialog.getWindow().setLayout(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT
                );
            }
        });

        playerDrawButton.setEnabled(false);

        animateText(npcMessage, message, 0);

        npcCloseButton.setOnClickListener(v -> {
            npcDialog.dismiss();
            if (enableAfterDismiss) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        playerDrawButton.setEnabled(true);
                    }
                }, 500);
            }
        });

        npcDialog.show();
    }

    private void showNpcDialogue(String message) {
        showNpcDialogue(message, true);
    }

    private void drawInitialHand() {
        playerHand.clear();
        for (int i = 0; i < 3; i++) {
            playerHand.add(drawCardFromReserve());
        }
        updateHandUI();
    }

    private BattleCard drawCardFromReserve() {
        if (player.getDeck().isEmpty()) return null;
        int index = new Random().nextInt(player.getDeck().size());
        return player.getDeck().get(index);
    }

    private void updateHandUI() {
        handLayout.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (BattleCard card : playerHand) {
            View cardView = inflater.inflate(R.layout.card_item, handLayout, false);
            ImageView cardImage = cardView.findViewById(R.id.cardImage);
            cardImage.setImageResource(card.getImageResId());
            cardView.setOnClickListener(v -> {
                battleLogText.setText("");
                playerHand.remove(card);
                updateHandUI();
                processPlayerTurnWithCard(card);
            });
            handLayout.addView(cardView);
        }
    }

    private void showHandSelection() {
        playerDrawnCard.setVisibility(View.INVISIBLE);
        // Do not hide the AI's card here.
        handLayout.setVisibility(View.VISIBLE);
        handLayout.bringToFront();
        handLayout.setElevation(100f);

        ViewGroup parent = (ViewGroup) handLayout.getParent();
        parent.requestLayout();
        parent.invalidate();

        playerDrawButton.setEnabled(false);
    }

    private void processPlayerTurnWithCard(BattleCard card) {
        battleLogText.setText("");
        animateDeckDraw(playerDeckPreview, playerDrawnCard, card.getImageResId(), new Runnable() {
            @Override
            public void run() {
                applyCardEffect(card, true);
                updateUI();
                currentRound++;
                updateRoundCounter();
                updateDeckPreview(player, playerDeckPreview);
                handLayout.setVisibility(View.GONE);
                afterTurnCheck();
                if (playerHealth > 0 && computerHealth > 0) {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            processComputerTurn();
                        }
                    }, 2000);
                }
            }
        });
    }

    private void processComputerTurn() {
        playerDrawButton.setEnabled(false);

        BattleCard card = computer.drawRandomCard();
        if (card != null) {
            battleLogText.setText("");
            animateDeckDraw(aiDeckPreview, aiDrawnCard, card.getImageResId(), new Runnable() {
                @Override
                public void run() {
                    applyCardEffect(card, false);
                    updateUI();
                    currentRound++;
                    updateRoundCounter();
                    updateDeckPreview(computer, aiDeckPreview);
                    afterTurnCheck();
                    if (playerHealth > 0 && computerHealth > 0) {
                        playerDrawButton.setEnabled(true);
                    }
                }
            });
        }
    }

    // In applyCardEffect, red cards (POISON, SLASH, PIERCING) are now allowed even if energy is 0.
    // If energy is available, subtract 1 and use full effect.
    // Otherwise, set the effective damage to 0.
    private void applyCardEffect(BattleCard card, boolean isPlayerTurn) {
        String logMessage = "";
        // Determine the effective value to use.
        int effectValue = card.getEffectValue();
        if (card.getType() == CardType.POISON || card.getType() == CardType.SLASH || card.getType() == CardType.PIERCING) {
            if (isPlayerTurn) {
                if (playerEnergy >= 1) {
                    playerEnergy--;
                } else {
                    effectValue = 0;
                }
            } else {
                if (computerEnergy >= 1) {
                    computerEnergy--;
                } else {
                    effectValue = 0;
                }
            }
        }
        switch (card.getType()) {
            case HEAL:
                if (isPlayerTurn) {
                    playerHealth += effectValue;
                    logMessage = "Player used HEAL and gained " + effectValue + " HP.";
                } else {
                    computerHealth += effectValue;
                    logMessage = "Computer used HEAL and gained " + effectValue + " HP.";
                }
                break;
            case ENERGY:
                if (isPlayerTurn) {
                    playerEnergy += effectValue;
                    logMessage = "Player used ENERGY and gained " + effectValue + " Energy.";
                } else {
                    computerEnergy += effectValue;
                    logMessage = "Computer used ENERGY and gained " + effectValue + " Energy.";
                }
                break;
            case SHIELD:
                if (isPlayerTurn) {
                    playerShield += effectValue;
                    logMessage = "Player used SHIELD and gained " + effectValue + " Shield.";
                } else {
                    computerShield += effectValue;
                    logMessage = "Computer used SHIELD and gained " + effectValue + " Shield.";
                }
                break;
            case POISON:
                final int ticks = 5;
                final int dps = (effectValue == 0) ? 0 : 3;
                Handler poisonHandler = new Handler();
                for (int i = 1; i <= ticks; i++) {
                    poisonHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (isPlayerTurn) {
                                computerHealth -= dps;
                            } else {
                                playerHealth -= dps;
                            }
                            updateUI();
                        }
                    }, i * 1000);
                }
                if (isPlayerTurn) {
                    logMessage = "Player used POISON! Opponent suffers " + dps + " DPS for 5 sec.";
                } else {
                    logMessage = "Computer used POISON! Player suffers " + dps + " DPS for 5 sec.";
                }
                break;
            case SLASH:
                if (isPlayerTurn) {
                    int damage = effectValue;
                    if (computerShield >= damage) {
                        computerShield -= damage;
                        damage = 0;
                    } else {
                        damage -= computerShield;
                        computerShield = 0;
                    }
                    computerHealth -= damage;
                    logMessage = "Player used SLASH, dealing " + damage + " damage.";
                } else {
                    int damage = effectValue;
                    if (playerShield >= damage) {
                        playerShield -= damage;
                        damage = 0;
                    } else {
                        damage -= playerShield;
                        playerShield = 0;
                    }
                    playerHealth -= damage;
                    logMessage = "Computer used SLASH, dealing " + damage + " damage.";
                }
                break;
            case PIERCING:
                if (isPlayerTurn) {
                    computerHealth -= effectValue;
                    logMessage = "Player used PIERCING, dealing " + effectValue + " damage (ignores shields).";
                } else {
                    playerHealth -= effectValue;
                    logMessage = "Computer used PIERCING, dealing " + effectValue + " damage (ignores shields).";
                }
                break;
            case NORMAL_ATTACK:
                if (isPlayerTurn) {
                    int damage = effectValue;
                    if (computerShield >= damage) {
                        computerShield -= damage;
                        damage = 0;
                    } else {
                        damage -= computerShield;
                        computerShield = 0;
                    }
                    computerHealth -= damage;
                    logMessage = "Player used NORMAL ATTACK, dealing " + damage + " damage.";
                } else {
                    int damage = effectValue;
                    if (playerShield >= damage) {
                        playerShield -= damage;
                        damage = 0;
                    } else {
                        damage -= playerShield;
                        playerShield = 0;
                    }
                    playerHealth -= damage;
                    logMessage = "Computer used NORMAL ATTACK, dealing " + damage + " damage.";
                }
                break;
        }
        battleLogText.setText(logMessage);
    }

    private void updateUI() {
        playerHealthText.setText("Player Health: " + playerHealth);
        computerHealthText.setText("Computer Health: " + computerHealth);
        playerShieldText.setText("Shield: " + playerShield);
        computerShieldText.setText("Shield: " + computerShield);
        playerEnergyText.setText("Energy: " + playerEnergy);
        computerEnergyText.setText("Energy: " + computerEnergy);
    }

    private void updateRoundCounter() {
        roundCounterText.setText("Round: " + currentRound + " / " + MAX_ROUNDS);
        View root = findViewById(R.id.battleEcoRoot);
        root.setBackgroundResource(BOSS_BACKGROUNDS[currentBossIndex]);
        aiCharacterImage.setImageResource(BOSS_IMAGES[currentBossIndex]);
    }

    private void afterTurnCheck() {
        boolean deathOccurred = false;
        if (playerHealth <= 0) {
            deathOccurred = true;
            showNpcDialogue("Player is defeated at round " + currentRound + "!", true);
            gameOver();
        } else if (computerHealth <= 0) {
            deathOccurred = true;
            showNpcDialogue("You defeated the boss at round " + currentRound + "!", true);
            proceedToNextBossOrWin();
        }
        if (!deathOccurred) {
            switch (currentRound) {
                case 3:
                    showEcoTipDialogue("Eco-Tip: Buying local produce cuts transportation emissions. Shop local, fight global warming!");
                    break;
                case 6:
                    showEcoTipDialogue("Eco-Tip: Reducing meat consumption lowers methane emissions. Try a plant-based meal!");
                    break;
                case 9:
                    showEcoTipDialogue("Eco-Tip: Avoid food waste! Plan your meals to minimize leftovers and trash.");
                    break;
                case 12:
                    showEcoTipDialogue("Eco-Tip: Growing your own herbs or vegetables at home can lower your carbon footprint!");
                    break;
            }
        }
    }

    private void showEcoTipDialogue(String message) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_npc_explanation, null);
        ImageView npcImage = dialogView.findViewById(R.id.npcImage);
        TextView npcMessage = dialogView.findViewById(R.id.npcMessage);
        Button npcCloseButton = dialogView.findViewById(R.id.npcCloseButton);

        npcCloseButton.setVisibility(View.GONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.TransparentFullScreenDialog);
        builder.setView(dialogView);
        final AlertDialog ecoDialog = builder.create();

        ecoDialog.setCancelable(false);
        ecoDialog.setCanceledOnTouchOutside(false);

        ecoDialog.setOnShowListener(dialogInterface -> {
            if (ecoDialog.getWindow() != null) {
                ecoDialog.getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });

        // Animate the message text
        animateText(npcMessage, message, 0);

        ecoDialog.show();

        // Automatically dismiss the eco tip after 3 seconds (adjust as needed)
        handler.postDelayed(() -> {
            ecoDialog.dismiss();
        }, 7000);
    }


    private void showBossIntroDialogue(int bossIndex) {
        switch (bossIndex) {
            case 0:
                showNpcDialogue(
                        "The Burger Boss emerges! Its high-carbon fast-food is threatening the forest. " +
                                "Opt for plant-based alternatives to reduce your carbon footprint!",
                        true
                );
                break;
            case 1:
                showNpcDialogue(
                        "The Cake Boss emerges! Its sugary chaos is polluting the forest. " +
                                "Remember to choose healthier, low-carbon foods like fruits and vegetables " +
                                "to reduce emissions from sugar-laden treats!",
                        true
                );
                break;
            case 2:
                showNpcDialogue(
                        "The Taco Boss has arrived! High-carbon meat production threatens the ecosystem. " +
                                "Opt for plant-based proteins to cut emissions and fight climate change!",
                        true
                );
                break;
            default:
                break;
        }
    }


    private void proceedToNextBossOrWin() {
        currentBossIndex++;
        if (currentBossIndex < BOSS_HEALTHS.length) {
            computerHealth = BOSS_HEALTHS[currentBossIndex];
            aiCharacterImage.setImageResource(BOSS_IMAGES[currentBossIndex]);
            findViewById(R.id.battleEcoRoot).setBackgroundResource(BOSS_BACKGROUNDS[currentBossIndex]);
            updateUI();
            updateRoundCounter();

            // Debug log: (replace with Log.d if you prefer)
            Toast.makeText(this, "Proceeding to boss index: " + currentBossIndex, Toast.LENGTH_SHORT).show();

            // Use a slight delay to ensure UI has settled.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showBossIntroDialogue(currentBossIndex);
                }
            }, 500);
        } else {
            showNpcDialogue("Congratulations! You have defeated all the bosses and saved the forest!", true);
            gameOver();
        }
    }


    private void gameOver() {
        playerDrawButton.setEnabled(false);
        if (playerHealth <= 0) {
            battleLogText.setText("Game Over! Player is defeated.");
        } else if (computerHealth <= 0) {
            battleLogText.setText("Game Over! Boss is defeated.");
        } else {
            battleLogText.setText("Game Over! Overtime continues until one is defeated.");
        }
    }

    private void updateDeckPreview(Player p, ImageView deckPreview) {
        BattleCard next = p.drawRandomCard();
        if (next != null) {
            if (next.getType() == CardType.HEAL ||
                    next.getType() == CardType.ENERGY ||
                    next.getType() == CardType.SHIELD) {
                deckPreview.setImageResource(R.drawable.green_cards_back);
            } else {
                deckPreview.setImageResource(R.drawable.red_cards_back);
            }
        }
    }

    private void animateText(TextView textView, String text, int index) {
        if (index < text.length()) {
            textView.setText(text.substring(0, index + 1));
            new Handler().postDelayed(() -> {
                animateText(textView, text, index + 1);
            }, 40);
        }
    }

    private void animateDeckDraw(final ImageView deckPreview, final ImageView drawnCard,
                                 final int newImageResId, final Runnable onAnimationEnd) {
        final MediaPlayer mp = MediaPlayer.create(BattleEcoActivity.this, R.raw.cardflip);
        mp.start();
        mp.setOnCompletionListener(MediaPlayer::release);

        drawnCard.setImageResource(newImageResId);
        drawnCard.setVisibility(View.INVISIBLE);

        final int[] startPos = new int[2];
        final int[] targetPos = new int[2];
        deckPreview.getLocationOnScreen(startPos);
        drawnCard.getLocationOnScreen(targetPos);

        final float deltaX = startPos[0] - targetPos[0];
        final float deltaY = startPos[1] - targetPos[1];

        drawnCard.setTranslationX(deltaX);
        drawnCard.setTranslationY(deltaY);
        drawnCard.setRotationY(0f);
        drawnCard.setVisibility(View.VISIBLE);

        ObjectAnimator flipOut = ObjectAnimator.ofFloat(drawnCard, "rotationY", 0f, 90f);
        flipOut.setDuration(200);
        flipOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                drawnCard.setRotationY(270f);
                ObjectAnimator flipIn = ObjectAnimator.ofFloat(drawnCard, "rotationY", 270f, 360f);
                flipIn.setDuration(200);
                flipIn.start();
            }
        });

        ObjectAnimator translateXAnim = ObjectAnimator.ofFloat(drawnCard, "translationX", deltaX, 0f);
        ObjectAnimator translateYAnim = ObjectAnimator.ofFloat(drawnCard, "translationY", deltaY, 0f);
        translateXAnim.setDuration(400);
        translateYAnim.setDuration(400);

        translateXAnim.start();
        translateYAnim.start();
        flipOut.start();

        translateYAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onAnimationEnd != null) {
                    onAnimationEnd.run();
                }
            }
        });
    }
}
