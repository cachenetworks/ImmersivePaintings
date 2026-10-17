package net.conczin.immersive_paintings;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumHand;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class GuiPaintingEditor extends GuiScreen {
    private final EnumHand hand;
    private final int entityId;
    private final int initialWidth;
    private final int initialHeight;
    private GuiTextField path;
    private GuiTextField widthBlocks;
    private GuiTextField heightBlocks;
    private String status = "Paste a public HTTPS PNG/JPG URL or local image path.";
    private boolean uploading;
    private int left;
    private int top;

    public GuiPaintingEditor(EnumHand hand) {
        this.hand = hand;
        this.entityId = -1;
        this.initialWidth = 1;
        this.initialHeight = 1;
    }

    public GuiPaintingEditor(int entityId, int initialWidth, int initialHeight) {
        this.hand = EnumHand.MAIN_HAND;
        this.entityId = entityId;
        this.initialWidth = initialWidth;
        this.initialHeight = initialHeight;
    }

    @Override
    public void initGui() {
        left = this.width / 2 - 140;
        top = this.height / 2 - 100;
        path = new GuiTextField(0, fontRenderer, left, top + 52, 280, 20);
        path.setMaxStringLength(2048);
        path.setText("");
        widthBlocks = new GuiTextField(1, fontRenderer, left, top + 98, 65, 20);
        widthBlocks.setText(Integer.toString(initialWidth));
        widthBlocks.setMaxStringLength(1);
        heightBlocks = new GuiTextField(2, fontRenderer, left + 100, top + 98, 65, 20);
        heightBlocks.setText(Integer.toString(initialHeight));
        heightBlocks.setMaxStringLength(1);
        path.setFocused(true);
        buttonList.add(new GuiButton(0, left, top + 130, 198, 20, entityId < 0 ? "Upload to held painting" : "Update painting on wall"));
        buttonList.add(new GuiButton(1, left + 205, top + 130, 75, 20, "Close"));
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id != 0 || uploading) return;
        try {
            int w = Integer.parseInt(widthBlocks.getText());
            int h = Integer.parseInt(heightBlocks.getText());
            if (w < 1 || w > 4 || h < 1 || h > 4) {
                status = "Width and height must be 1, 2, 3 or 4.";
                return;
            }
            final String imageSource = path.getText().trim();
            if (imageSource.isEmpty()) {
                status = "Paste an HTTPS image URL or enter a local PNG/JPG path.";
                return;
            }
            final String sourceLower = imageSource.toLowerCase(Locale.ROOT);
            if (sourceLower.contains("://") && !sourceLower.startsWith("https://")) {
                status = "Only public HTTPS URLs are supported; use https://";
                return;
            }
            uploading = true;
            button.enabled = false;
            status = sourceLower.startsWith("https://") ? "Downloading image..." : "Reading image...";
            final File gameDirectory = mc.mcDataDir;

            // Read/download/resize off the render thread so Minecraft doesn't freeze.
            Thread worker = new Thread(() -> {
                try {
                    byte[] png;
                    if (sourceLower.startsWith("https://")) {
                        png = ImageOps.readRemote(imageSource);
                    } else {
                        File image = new File(imageSource);
                        if (!image.isAbsolute()) image = new File(gameDirectory, imageSource);
                        png = ImageOps.readLocal(image);
                    }
                    mc.addScheduledTask(() -> {
                        if (mc.currentScreen != this) return;
                        ImmersivePaintings.NETWORK.sendToServer(
                            new MessageUpload(hand, entityId, w, h, png));
                        uploading = false;
                        button.enabled = true;
                        status = "Sent " + (png.length / 1024) +
                                 " KiB. Check chat for server confirmation.";
                    });
                } catch (IOException | RuntimeException error) {
                    final String message = error.getMessage() != null
                        ? error.getMessage() : "Unable to open image.";
                    mc.addScheduledTask(() -> {
                        if (mc.currentScreen != this) return;
                        uploading = false;
                        button.enabled = true;
                        status = message;
                    });
                }
            }, "ImmersivePaintings-ImageLoader");
            worker.setDaemon(true);
            worker.start();
        } catch (NumberFormatException e) {
            status = "Enter width and height as numbers from 1 to 4.";
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (path.textboxKeyTyped(typedChar, keyCode)) return;
        if (widthBlocks.textboxKeyTyped(typedChar, keyCode)) return;
        if (heightBlocks.textboxKeyTyped(typedChar, keyCode)) return;
        if (keyCode == Keyboard.KEY_TAB) {
            if (path.isFocused()) {
                path.setFocused(false);
                widthBlocks.setFocused(true);
            } else if (widthBlocks.isFocused()) {
                widthBlocks.setFocused(false);
                heightBlocks.setFocused(true);
            } else {
                heightBlocks.setFocused(false);
                path.setFocused(true);
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        super.mouseClicked(x, y, button);
        path.mouseClicked(x, y, button);
        widthBlocks.mouseClicked(x, y, button);
        heightBlocks.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        path.updateCursorCounter();
        widthBlocks.updateCursorCounter();
        heightBlocks.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, entityId < 0 ? "Import painting image" : "Edit placed painting",
                           this.width / 2, top + 12, 0xFFFFFF);
        fontRenderer.drawString("Public HTTPS PNG/JPG URL or local file path:",
                                left, top + 38, 0xDDDDDD);
        fontRenderer.drawString("Width (blocks):", left, top + 84, 0xDDDDDD);
        fontRenderer.drawString("Height (blocks):", left + 100, top + 84, 0xDDDDDD);
        path.drawTextBox();
        widthBlocks.drawTextBox();
        heightBlocks.drawTextBox();
        fontRenderer.drawSplitString(status, left, top + 160, 280, 0xFFFFAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
