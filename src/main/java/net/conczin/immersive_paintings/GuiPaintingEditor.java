package net.conczin.immersive_paintings;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumHand;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;

public class GuiPaintingEditor extends GuiScreen {
    private final EnumHand hand;
    private GuiTextField path;
    private GuiTextField widthBlocks;
    private GuiTextField heightBlocks;
    private String status = "Select a local PNG or JPG to upload.";
    private int left;
    private int top;

    public GuiPaintingEditor(EnumHand hand) {
        this.hand = hand;
    }

    @Override
    public void initGui() {
        left = this.width / 2 - 140;
        top = this.height / 2 - 100;
        path = new GuiTextField(0, fontRenderer, left, top + 52, 280, 20);
        path.setMaxStringLength(512);
        path.setText("config/immersive_paintings/example.png");
        widthBlocks = new GuiTextField(1, fontRenderer, left, top + 98, 65, 20);
        widthBlocks.setText("1");
        widthBlocks.setMaxStringLength(1);
        heightBlocks = new GuiTextField(2, fontRenderer, left + 100, top + 98, 65, 20);
        heightBlocks.setText("1");
        heightBlocks.setMaxStringLength(1);
        path.setFocused(true);
        buttonList.add(new GuiButton(0, left, top + 130, 198, 20, "Upload to held painting"));
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
        if (button.id != 0) return;
        try {
            int w = Integer.parseInt(widthBlocks.getText());
            int h = Integer.parseInt(heightBlocks.getText());
            if (w < 1 || w > 4 || h < 1 || h > 4) {
                status = "Width and height must be 1, 2, 3 or 4.";
                return;
            }
            File image = new File(path.getText().trim());
            if (!image.isAbsolute()) image = new File(mc.mcDataDir, path.getText().trim());
            byte[] png = ImageOps.readLocal(image);
            ImmersivePaintings.NETWORK.sendToServer(new MessageUpload(hand, w, h, png));
            status = "Sent image (" + (png.length / 1024) + " KiB). Check chat for confirmation.";
        } catch (NumberFormatException e) {
            status = "Enter width and height as numbers from 1 to 4.";
        } catch (IOException e) {
            status = e.getMessage() == null ? "Could not open image." : e.getMessage();
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
        drawCenteredString(fontRenderer, "Immersive Paintings — Import Image",
                           this.width / 2, top + 12, 0xFFFFFF);
        fontRenderer.drawString("Local PNG/JPG path (absolute or relative to .minecraft):",
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
