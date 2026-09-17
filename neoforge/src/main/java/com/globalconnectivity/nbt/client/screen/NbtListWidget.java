package com.globalconnectivity.nbt.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * A selection list whose contents can be scrolled by dragging anywhere inside
 * it (in addition to the mouse wheel and the scrollbar).
 */
public final class NbtListWidget extends ObjectSelectionList<NbtListWidget.Row> {

    public record RowData(String title, String detail, String value, int titleColor) {}

    public static final class Row extends ObjectSelectionList.Entry<Row> {
        public final RowData data;
        private final NbtListWidget parent;
        private long lastClickMs;

        private Row(NbtListWidget parent, RowData data) {
            this.parent = parent;
            this.data = data;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.data.title());
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (this.parent.getSelected() == this) {
                graphics.fill(left, top, left + width, top + height, 0x804080FF);
            } else if (hovered) {
                graphics.fill(left, top, left + width, top + height, 0x30FFFFFF);
            }
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, font.plainSubstrByWidth(this.data.title(), width - 8), left + 4, top + 5, this.data.titleColor());
            if (!this.data.detail().isEmpty()) {
                graphics.drawString(font, font.plainSubstrByWidth(this.data.detail(), width - 8), left + 4, top + 15, 0xFFA0A0A0);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && this.parent.selectHandler != null) {
                this.parent.setSelected(this);
                long now = System.currentTimeMillis();
                boolean doubleClick = now - this.lastClickMs < 450L;
                this.lastClickMs = now;
                this.parent.selectHandler.accept(this, doubleClick);
                return true;
            }
            return false;
        }
    }

    final BiConsumer<Row, Boolean> selectHandler;
    private boolean dragScrolling;
    private double lastDragY;

    public NbtListWidget(Minecraft minecraft, int x, int y, int width, int height, int itemHeight,
                         BiConsumer<Row, Boolean> selectHandler) {
        super(minecraft, width, height, y, itemHeight);
        this.selectHandler = selectHandler;
        this.setX(x);
    }

    public void setEntries(List<RowData> entries) {
        this.clearEntries();
        for (RowData d : entries) {
            this.addEntry(new Row(this, d));
        }
        this.setScrollAmount(0);
    }

    public Row addRow(String title, String detail, String value, int titleColor) {
        Row row = new Row(this, new RowData(title, detail, value, titleColor));
        this.addEntry(row);
        return row;
    }

    public String selectedValue() {
        Row row = this.getSelected();
        return row == null ? null : row.data.value();
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getX() + this.width - 7;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= this.getX() && mouseX < getScrollbarPosition()
                && mouseY >= this.getY() && mouseY <= this.getY() + this.height) {
            this.dragScrolling = true;
            this.lastDragY = mouseY;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragScrolling && button == 0) {
            this.setScrollAmount(this.getScrollAmount() - (mouseY - this.lastDragY));
            this.lastDragY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.dragScrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
