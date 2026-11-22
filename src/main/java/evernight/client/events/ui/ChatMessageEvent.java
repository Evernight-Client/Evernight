package evernight.client.events.ui;

import net.minecraft.client.GuiMessage.Line;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import evernight.client.events.core.CancellableEvent;
import evernight.client.events.core.EventStage;

public final class ChatMessageEvent extends CancellableEvent {
    private final EventStage stage;
    private final GuiMessageTag tag;
    private MessageSignature signature;
    private Component message;
    private Line line;
    private Component originalMessage;
    private String originalText;

    public ChatMessageEvent(GuiMessageTag tag, MessageSignature signature, Component message) {
        this.tag = tag;
        this.signature = signature;
        this.message = message;
        this.stage = EventStage.Pre;
        this.originalMessage = message == null ? null : Component.empty().append(message);
        this.originalText = message == null ? null : message.getString();
    }

    public ChatMessageEvent(Line line) {
        this.line = line;
        this.stage = EventStage.Post;
        this.tag = null;
    }

    public EventStage getStage() {
        return this.stage;
    }

    public MessageSignature getSignature() {
        return this.signature;
    }

    public void setSignature(MessageSignature signature) {
        this.signature = signature;
    }

    public Line getLine() {
        return this.line;
    }

    public void setLine(Line line) {
        this.line = line;
    }

    public Component getMessage() {
        return this.message;
    }

    public void setMessage(Component message) {
        this.message = message;
    }

    public Component getOriginalMessage() {
        return this.originalMessage;
    }

    public String getOriginalText() {
        return this.originalText;
    }

    public GuiMessageTag getTag() {
        return this.tag;
    }
}