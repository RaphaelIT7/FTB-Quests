package dev.ftb.mods.ftbquests.client.gui.quests;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigFromStringScreen;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.ChangeChapterGroupScreen;
import dev.ftb.mods.ftbquests.client.gui.ContextMenuBuilder;
import dev.ftb.mods.ftbquests.net.CreateObjectMessage;
import dev.ftb.mods.ftbquests.net.MoveChapterGroupMessage;
import dev.ftb.mods.ftbquests.net.MoveChapterMessage;
import dev.ftb.mods.ftbquests.net.ToggleChapterPinnedMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.theme.property.ThemeProperties;
import dev.ftb.mods.ftbquests.util.TextUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ChapterPanel extends Panel {
	public static final Icon ARROW_COLLAPSED = Icon.getIcon("ftbquests:textures/gui/arrow_collapsed.png");
	public static final Icon ARROW_EXPANDED = Icon.getIcon("ftbquests:textures/gui/arrow_expanded.png");

	private final QuestScreen questScreen;
	boolean expanded = isPinned();

	public ChapterPanel(Panel panel) {
		super(panel);
		questScreen = (QuestScreen) panel.getGui();
	}

	@Override
	public boolean checkMouseOver(int mouseX, int mouseY) {
		if (questScreen.viewQuestPanel.viewingQuest()) {
			return false;
		}

		return super.checkMouseOver(mouseX, mouseY);
	}

	@Override
	public void addWidgets() {
		add(new ModpackButton(this, questScreen.file));

        /*
		if (Platform.isModLoaded("ftbmoney")) {
			add(new OpenShopButton(this));
			Color4I borderColor = ThemeProperties.WIDGET_BORDER.get(treeGui.selectedChapter);
			add(new ColorWidget(this, borderColor, null).setPosAndSize(1, 0, width - 2, 1));
		}
		 */

		boolean canEdit = questScreen.file.canEdit();

		for (Chapter chapter : questScreen.file.getDefaultChapterGroup().getVisibleChapters(questScreen.file.selfTeamData)) {
			add(new ChapterButton(this, chapter));
		}

		if (canEdit) {
			//add(new AddChapterButton(this, questScreen.file.defaultChapterGroup));
		}

		questScreen.file.forAllChapterGroups(group -> {
			if (!group.isDefaultGroup()) {
				ChapterGroupButton button = new ChapterGroupButton(this, group);
				if (canEdit || !button.visibleChapters.isEmpty()) {
					add(button);
					if (!group.isGuiCollapsed()) {
						button.visibleChapters.forEach(chapter -> add(new ChapterButton(this, chapter)));
					}
				}
			}
		});
	}

	@Override
	public void alignWidgets() {
		int wd = 100;

		for (Widget w : widgets) {
			wd = Math.min(Math.max(wd, ((ListButton) w).getActualWidth(questScreen)), 800);
		}

		setPosAndSize(((expanded || isPinned()) && !questScreen.isViewingQuest()) ? 0 : -wd, 0, wd, questScreen.height);

		for (Widget w : widgets) {
			w.setWidth(wd);
		}

		align(WidgetLayout.VERTICAL);

		if (getContentHeight() <= height) {
			setScrollY(0);
		}
	}

	@Override
	public void updateMouseOver(int mouseX, int mouseY) {
		super.updateMouseOver(mouseX, mouseY);

		if (expanded && !isPinned() && !isMouseOver()) {
			setExpanded(false);
		}
	}

	@Override
	public int getX() {
		return (expanded || isPinned()) && !questScreen.isViewingQuest() ? 0 : -width;
	}

	@Override
	public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
		theme.drawContextMenuBackground(graphics, x, y, w, h);
	}

	@Override
	public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 600);
		RenderSystem.enableDepthTest();
		super.draw(graphics, theme, x, y, w, h);
		graphics.pose().popPose();
	}

	public void setExpanded(boolean b) {
		expanded = b;
	}

	boolean isPinned() {
		return ClientQuestFile.INSTANCE.selfTeamData.isChapterPinned(Minecraft.getInstance().player);
	}


	public static abstract class ListButton extends Button {
		public final ChapterPanel chapterPanel;

		public ListButton(ChapterPanel panel, Component t, Icon i) {
			super(panel, t, i);
			setSize(100, 14);
			chapterPanel = panel;
		}

		public int getActualWidth(QuestScreen screen) {
			return screen.getTheme().getStringWidth(title) + 20;
		}

		@Override
		public void addMouseOverText(TooltipList list) {
		}

		public Optional<PositionedIngredient> getIngredientUnderMouse() {
			return PositionedIngredient.of(icon.getIngredient(), this);
		}
	}

	public static class ModpackButton extends ListButton {
		public ModpackButton(ChapterPanel panel, ClientQuestFile f) {
			super(panel, f.getTitle(), f.getIcon());
			setSize(100, 18);
		}

		@Override
		public void onClicked(MouseButton button) {
			if (getMouseX() > getX() + width - 18) {
				playClickSound();
				new ToggleChapterPinnedMessage().sendToServer();
			} else if (chapterPanel.questScreen.file.canEdit() && getMouseX() > getX() + width - 34) {
				playClickSound();

				List<ContextMenuItem> contextMenu = new ArrayList<>();
				contextMenu.add(new ContextMenuItem(Component.translatable("ftbquests.chapter"), ThemeProperties.ADD_ICON.get(), () -> {
					StringConfig c = new StringConfig(Pattern.compile("^.+$"));
					EditConfigFromStringScreen.open(c, "", "", accepted -> {
						chapterPanel.questScreen.openGui();

						if (accepted && !c.getValue().isEmpty()) {
							Chapter chapter = new Chapter(0L, chapterPanel.questScreen.file, chapterPanel.questScreen.file.getDefaultChapterGroup());
							chapter.setRawTitle(c.getValue());
							CompoundTag extra = new CompoundTag();
							extra.putLong("group", 0L);
							new CreateObjectMessage(chapter, extra).sendToServer();
						}

						run();
					});
				}));

				contextMenu.add(new ContextMenuItem(Component.translatable("ftbquests.chapter_group"), ThemeProperties.ADD_ICON.get(), () -> {
					playClickSound();
					StringConfig c = new StringConfig(Pattern.compile("^.+$"));
					EditConfigFromStringScreen.open(c, "", "", accepted -> {
						chapterPanel.questScreen.openGui();

						if (accepted) {
							ChapterGroup group = new ChapterGroup(0L, ClientQuestFile.INSTANCE);
							group.setRawTitle(c.getValue());
							new CreateObjectMessage(group, null).sendToServer();
						}
					});
				}));

				chapterPanel.questScreen.openContextMenu(contextMenu);
			}
		}

		@Override
		public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
			GuiHelper.setupDrawing();

			if (isMouseOver()) {
				Color4I.WHITE.withAlpha(40).draw(graphics, x + 1, y + 1, w - 2, h - 2);
			}

			ChatFormatting f = isMouseOver() ? ChatFormatting.WHITE : ChatFormatting.GRAY;

			icon.draw(graphics, x + 2, y + 3, 12, 12);
			theme.drawString(graphics, Component.literal("").append(title).withStyle(f), x + 16, y + 5);

			ThemeProperties.WIDGET_BORDER.get(ClientQuestFile.INSTANCE).draw(graphics, x, y + h - 1, w, 1);

			boolean canEdit = chapterPanel.questScreen.file.canEdit();

			(chapterPanel.isPinned() ? ThemeProperties.PIN_ICON_ON : ThemeProperties.PIN_ICON_OFF).get().draw(graphics, x + w - 16, y + 3, 12, 12);

			if (canEdit) {
				ThemeProperties.ADD_ICON.get().draw(graphics, x + w - 31, y + 3, 12, 12);
			}
		}

		@Override
		public int getActualWidth(QuestScreen screen) {
			boolean canEdit = chapterPanel.questScreen.file.canEdit();
			return screen.getTheme().getStringWidth(title) + 36 + (canEdit ? 16 : 0);
		}

		@Override
		public void addMouseOverText(TooltipList list) {
			chapterPanel.questScreen.addInfoTooltip(list, chapterPanel.questScreen.file);

			if (getMouseX() > getX() + width - 18) {
				list.string(chapterPanel.isPinned() ? "Stays open" : "Doesn't stay open");
			} else if (chapterPanel.questScreen.file.canEdit() && getMouseX() > getX() + width - 34) {
				list.translate("gui.add");
			}
		}
	}

	public static class ChapterGroupButton extends ListButton {
		public final ChapterGroup group;
		public final List<Chapter> visibleChapters;

		public ChapterGroupButton(ChapterPanel panel, ChapterGroup g) {
			super(panel, g.getTitle(), g.getIcon());
			setSize(100, 18);
			group = g;
			visibleChapters = g.getVisibleChapters(panel.questScreen.file.selfTeamData);
		}

		@Override
		public void onClicked(MouseButton button) {
			if (chapterPanel.questScreen.file.canEdit() && getMouseX() > getX() + width - 15) {
				playClickSound();

				StringConfig c = new StringConfig(Pattern.compile("^.+$"));
				EditConfigFromStringScreen.open(c, "", "", accepted -> {
					chapterPanel.questScreen.openGui();

					if (accepted && !c.getValue().isEmpty()) {
						Chapter chapter = new Chapter(0L, chapterPanel.questScreen.file, chapterPanel.questScreen.file.getDefaultChapterGroup());
						chapter.setRawTitle(c.getValue());
						CompoundTag extra = new CompoundTag();
						extra.putLong("group", group.id);
						new CreateObjectMessage(chapter, extra).sendToServer();
					}

					run();
				});

				return;
			}

			if (chapterPanel.questScreen.file.canEdit() && button.isRight() && !group.isDefaultGroup()) {
				ContextMenuBuilder.create(group, chapterPanel.questScreen).insertAtTop(List.of(
						new ContextMenuItem(Component.translatable("gui.move"), ThemeProperties.MOVE_UP_ICON.get(),
								() -> new MoveChapterGroupMessage(group.id, true).sendToServer())
								.setEnabled(!group.isFirstGroup())
								.setCloseMenu(false),
						new ContextMenuItem(Component.translatable("gui.move"), ThemeProperties.MOVE_DOWN_ICON.get(),
								() -> new MoveChapterGroupMessage(group.id, false).sendToServer())
								.setEnabled(!group.isLastGroup())
								.setCloseMenu(false)
				)).openContextMenu(chapterPanel.questScreen);
				return;
			}

			group.toggleCollapsed();
			parent.refreshWidgets();
		}

		@Override
		public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
			GuiHelper.setupDrawing();

			if (isMouseOver()) {
				Color4I.WHITE.withAlpha(40).draw(graphics, x + 1, y, w - 2, h);
			}

			ChatFormatting f = isMouseOver() ? ChatFormatting.WHITE : ChatFormatting.GRAY;

			(group.isGuiCollapsed() ? ARROW_COLLAPSED : ARROW_EXPANDED).withColor(Color4I.getChatFormattingColor(f)).draw(graphics, x + 3, y + 5, 8, 8);
			theme.drawString(graphics, Component.literal("").append(title).withStyle(f), x + 15, y + 5);

			boolean canEdit = chapterPanel.questScreen.file.canEdit();

			if (canEdit) {
				ThemeProperties.ADD_ICON.get().draw(graphics, x + w - 14, y + 3, 12, 12);
			}
		}

		@Override
		public int getActualWidth(QuestScreen screen) {
			boolean canEdit = chapterPanel.questScreen.file.canEdit();
			return screen.getTheme().getStringWidth(title) + 20 + (canEdit ? 16 : 0);
		}

		@Override
		public void addMouseOverText(TooltipList list) {
			chapterPanel.questScreen.addInfoTooltip(list, group);
		}
	}

	public static class ChapterButton extends ListButton {
		private final Chapter chapter;
		private final List<? extends Component> description;

		public ChapterButton(ChapterPanel panel, Chapter c) {
			super(panel, c.getTitle(), c.getIcon());

			chapter = c;
			description = chapter.getRawSubtitle().stream()
					.map(line -> TextUtils.parseRawText(line).copy().withStyle(ChatFormatting.GRAY))
					.toList();
		}

		@Override
		public void onClicked(MouseButton button) {
			if (chapterPanel.questScreen.file.canEdit() || chapter.hasAnyVisibleChildren()) {
				playClickSound();

				if (chapterPanel.questScreen.selectedChapter != chapter) {
					chapterPanel.questScreen.open(chapter, false);
				}
			}

			if (chapterPanel.questScreen.file.canEdit() && button.isRight()) {
				ContextMenuBuilder.create(chapter, chapterPanel.questScreen).insertAtTop(List.of(
						new ContextMenuItem(Component.translatable("gui.move"), ThemeProperties.MOVE_UP_ICON.get(),
								() -> new MoveChapterMessage(chapter.id, true).sendToServer())
								.setEnabled(chapter.getIndex() > 0).setCloseMenu(false),
						new ContextMenuItem(Component.translatable("gui.move"), ThemeProperties.MOVE_DOWN_ICON.get(),
								() -> new MoveChapterMessage(chapter.id, false).sendToServer())
								.setEnabled(chapter.getIndex() < chapter.getGroup().getChapters().size() - 1).setCloseMenu(false),
						new ContextMenuItem(Component.translatable("ftbquests.gui.change_group"), Icons.COLOR_RGB,
								() -> new ChangeChapterGroupScreen(chapter, chapterPanel.questScreen).openGui())
				)).openContextMenu(chapterPanel.questScreen);
			}
		}

		@Override
		public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
			GuiHelper.setupDrawing();

			if (isMouseOver()) {
				Color4I.WHITE.withAlpha(40).draw(graphics, x + 1, y, w - 2, h);
			}

			Color4I c = chapter.getProgressColor(chapterPanel.questScreen.file.selfTeamData, !isMouseOver());
			int xOff = chapter.getGroup().isDefaultGroup() ? 0 : 7;

			icon.draw(graphics, x + 2 + xOff, y + 1, 12, 12);
			MutableComponent text = Component.literal("").append(title).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(c.rgb())));
			if (chapterPanel.questScreen.selectedChapter != null && chapter.id == chapterPanel.questScreen.selectedChapter.id) {
				text.append(Component.literal(" ◀").withStyle(ChatFormatting.GRAY));
			}
			theme.drawString(graphics, text, x + 16 + xOff, y + 3);

			GuiHelper.setupDrawing();

			if (!chapter.hasAnyVisibleChildren()) {
				ThemeProperties.CLOSE_ICON.get().draw(graphics, x + w - 12, y + 3, 8, 8);
			} else if (chapterPanel.questScreen.file.selfTeamData.hasUnclaimedRewards(Minecraft.getInstance().player.getUUID(), chapter)) {
				ThemeProperties.ALERT_ICON.get().draw(graphics, x + w - 12, y + 3, 8, 8);
			}
		}

		@Override
		public void addMouseOverText(TooltipList list) {
			chapterPanel.questScreen.addInfoTooltip(list, chapter);

			for (Component s : description) {
				list.add(s);
			}
		}

		@Override
		public int getActualWidth(QuestScreen screen) {
			int extra = chapter.getGroup().isDefaultGroup() ? 0 : 7;

			if (!chapter.hasAnyVisibleChildren() || chapterPanel.questScreen.file.selfTeamData.hasUnclaimedRewards(Minecraft.getInstance().player.getUUID(), chapter)) {
				// space for the "X" marker
				extra += 16;
			}

			return screen.getTheme().getStringWidth(title) + 20 + extra;
		}
	}
}
