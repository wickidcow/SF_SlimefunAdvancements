package me.char321.sfadvancements.core.criteria.completer;

import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.GuideHistory;
import me.char321.sfadvancements.SFAdvancements;
import me.char321.sfadvancements.api.criteria.Criterion;
import me.char321.sfadvancements.api.criteria.SearchCriterion;
import me.char321.sfadvancements.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchCriterionCompleter implements CriterionCompleter {
    private static final String JEG_HISTORY = "com.balugaq.jeg.api.patches.JEGGuideHistory";
    private static final String JEG_SEARCH_ENTRY =
        "com.balugaq.jeg.api.patches.JEGGuideEntry$SearchTermEntry";

    private final Map<String, List<SearchCriterion>> criteria = new HashMap<>();

    public SearchCriterionCompleter() {
        Field queueField;
        Method getIndexedObject;
        try {
            queueField = GuideHistory.class.getDeclaredField("queue");
            queueField.setAccessible(true);
            getIndexedObject = Class.forName("io.github.thebusybiscuit.slimefun4.core.guide.GuideEntry")
                .getDeclaredMethod("getIndexedObject");
            getIndexedObject.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return;
        }

        Class<?> jegHistoryClass = null;
        Class<?> jegSearchEntryClass = null;
        Method jegGetLastEntry = null;
        Method jegEntryGet = null;
        try {
            jegHistoryClass = Class.forName(JEG_HISTORY);
            jegSearchEntryClass = Class.forName(JEG_SEARCH_ENTRY);
            jegGetLastEntry = jegHistoryClass.getMethod("getLastEntry", boolean.class);
            jegEntryGet = jegSearchEntryClass.getMethod("get");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // JEG is an optional soft-dependency. Classic Slimefun history remains the fallback.
        }

        final Class<?> resolvedJegHistoryClass = jegHistoryClass;
        final Class<?> resolvedJegSearchEntryClass = jegSearchEntryClass;
        final Method resolvedJegGetLastEntry = jegGetLastEntry;
        final Method resolvedJegEntryGet = jegEntryGet;

        Bukkit.getScheduler().runTaskTimer(SFAdvancements.instance(), () -> {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                PlayerProfile.get(onlinePlayer, profile -> {
                    // In some environments, two calls to profile.getGuideHistory() may return different
                    // implementations. Read it once and route by the actual runtime type.
                    GuideHistory history = profile.getGuideHistory();
                    if (resolvedJegHistoryClass != null
                        && resolvedJegSearchEntryClass != null
                        && resolvedJegGetLastEntry != null
                        && resolvedJegEntryGet != null
                        && resolvedJegHistoryClass.isInstance(history)) {
                        try {
                            Object entry = resolvedJegGetLastEntry.invoke(history, false);
                            if (entry != null && resolvedJegSearchEntryClass.isInstance(entry)) {
                                Object search = resolvedJegEntryGet.invoke(entry);
                                if (search instanceof String term) {
                                    Utils.runSync(() -> onSearch(onlinePlayer, term));
                                }
                            }
                        } catch (ReflectiveOperationException e) {
                            e.printStackTrace();
                        }
                        return;
                    }

                    try {
                        Deque<?> queue = (Deque<?>) queueField.get(history);
                        if (!queue.isEmpty()) {
                            Object str = getIndexedObject.invoke(queue.getLast());
                            if (str instanceof String term) {
                                Utils.runSync(() -> onSearch(onlinePlayer, term));
                            }
                        }
                    } catch (ReflectiveOperationException e) {
                        e.printStackTrace();
                    }
                });
            }
        }, 10, 10);
    }

    private void onSearch(Player player, String str) { //TODO make not trigger multiple times for one search
        List<SearchCriterion> searchCriteria = criteria.get(str);
        if (searchCriteria == null) return;

        for (SearchCriterion criterion : searchCriteria) {
            criterion.perform(player);
        }
    }

    @Override
    public void register(Criterion criterion) {
        if (!(getCriterionClass().isInstance(criterion))) {
            throw new IllegalArgumentException("criterion must be a " + getCriterionClass().getName());
        }

        SearchCriterion criterion1 = (SearchCriterion) criterion;
        criteria.computeIfAbsent(criterion1.getSearch(), k -> new ArrayList<>()).add(criterion1);
    }

    @Override
    public Class<? extends Criterion> getCriterionClass() {
        return SearchCriterion.class;
    }

    @Override
    public void reload() {
        criteria.clear();
    }
}
