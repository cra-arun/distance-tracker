package com.example.distance_track;

public class Badgemodel {
    private String title;
    private int unlockedDrawable;
    private boolean unlocked;
    private int progress; // 0 to 100

    public Badgemodel(String title, int badge_locked, int unlockedDrawable, boolean unlocked, int progress) {
        this.title = title;
        this.unlockedDrawable = unlockedDrawable;
        this.unlocked = unlocked;
        this.progress = progress;
    }

    public String getTitle() {
        return title;
    }

    public int getUnlockedDrawable() {
        return unlockedDrawable;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public int getProgress() {
        return progress;
    }
}
