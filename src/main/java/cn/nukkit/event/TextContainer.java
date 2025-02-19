package cn.nukkit.event;

import cn.nukkit.Server;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class TextContainer implements Cloneable {
    protected String text;

    public TextContainer(String text) {
        this.text = text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return this.getText();
    }

    @Override
    public TextContainer clone() {
        try {
            return (TextContainer) super.clone();
        } catch (CloneNotSupportedException e) {
            Server.getInstance().logger.logException(e);
        }
        return null;
    }
}
