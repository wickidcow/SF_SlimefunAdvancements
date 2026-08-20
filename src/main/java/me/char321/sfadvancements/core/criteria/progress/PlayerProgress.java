package me.char321.sfadvancements.core.criteria.progress;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import me.char321.sfadvancements.SFAdvancements;
import me.char321.sfadvancements.api.Advancement;
import me.char321.sfadvancements.api.criteria.Criterion;
import me.char321.sfadvancements.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * a per-player object that stores their advancement progress <br>
 *
 * json <br>
 *
 */
public class PlayerProgress {
    private final UUID player;
    private final Map<NamespacedKey, AdvancementProgress> progressMap = new HashMap<>();

    private PlayerProgress(UUID player) {
        this.player = player;
    }

    public static PlayerProgress get(Player player) {
        return get(player.getUniqueId());
    }

    public static PlayerProgress get(UUID player) {
        PlayerProgress res = new PlayerProgress(player);

        File advancementsFolder = new File(SFAdvancements.instance().getDataFolder(), "/advancements");
        File f = new File(advancementsFolder, player.toString() + ".json");
        if (!f.exists()) {
            return res;
        }

        try {
            res.loadFromObject(readProgressFile(f));
        } catch (IOException | RuntimeException e) {
            SFAdvancements.logger().log(Level.WARNING,
                "Invalid advancement progress data for " + player + ". Attempting recovery.", e);
            recoverCorruptProgress(res, f);
        }
        return res;
    }

    public synchronized void doCriterion(Criterion criterion) {
        NamespacedKey adv = criterion.getAdvancement();
        progressMap.computeIfAbsent(adv, AdvancementProgress::new);

        AdvancementProgress advProgress = progressMap.get(adv);
        if (advProgress.done) {
            return;
        }

        for (CriteriaProgress progress : advProgress.criteria) {
            if (!progress.id.equals(criterion.getId())) {
                continue;
            }

            if (progress.progress < criterion.getCount()) {
                progress.progress++;
                if (progress.progress >= criterion.getCount()) {
                    progress.done = true;
                    advProgress.updateDone();
                }
            }
        }
    }

    public synchronized void completeCriterion(Criterion criterion) {
        NamespacedKey adv = criterion.getAdvancement();
        AdvancementProgress progress = progressMap.computeIfAbsent(adv, AdvancementProgress::new);

        for (CriteriaProgress criteriaProgress : progress.criteria) {
            if (!criteriaProgress.id.equals(criterion.getId())) {
                continue;
            }

            if (criteriaProgress.done) {
                return;
            }

            criteriaProgress.done = true;
            criteriaProgress.progress = criterion.getCount();
            progress.updateDone();
        }
    }

    public synchronized int getCriterionProgress(Criterion cri) {
        NamespacedKey adv = cri.getAdvancement();
        if (!progressMap.containsKey(adv)) {
            return 0;
        }

        AdvancementProgress advProgress = progressMap.get(adv);
        for (CriteriaProgress progress : advProgress.criteria) {
            if (progress.id.equals(cri.getId())) {
                return progress.progress;
            }
        }
        throw new IllegalStateException();
    }

    public synchronized boolean revokeAdvancement(NamespacedKey adv) {
        if (!progressMap.containsKey(adv)) {
            return false;
        }
        progressMap.get(adv).done = false;
        for (CriteriaProgress progress : progressMap.get(adv).criteria) {
            progress.done = false;
            progress.progress = 0;
        }
        Utils.fromKey(adv).revoke(Bukkit.getPlayer(player));
        return true;
    }

    public synchronized List<NamespacedKey> getCompletedAdvancements() {
        List<NamespacedKey> res = new ArrayList<>();
        for (Map.Entry<NamespacedKey, AdvancementProgress> entry : progressMap.entrySet()) {
            if (entry.getValue().done) {
                res.add(entry.getKey());
            }
        }
        return res;
    }

    private void loadFromObject(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            NamespacedKey advkey = NamespacedKey.fromString(entry.getKey());
            if (advkey == null || !Utils.isValidAdvancement(advkey)) {
                SFAdvancements.warn("Unknown advancement: " + entry.getKey());
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                SFAdvancements.warn("Invalid advancement progress entry for " + entry.getKey() + "; skipping it");
                continue;
            }

            AdvancementProgress newprogress = new AdvancementProgress(advkey);
            progressMap.put(advkey, newprogress);
            newprogress.loadFromObject(entry.getValue().getAsJsonObject());
        }
    }

    public synchronized void save() throws IOException {
        File advancementsFolder = new File(SFAdvancements.instance().getDataFolder(), "/advancements");
        Path folder = advancementsFolder.toPath();
        Files.createDirectories(folder);

        Path target = folder.resolve(player + ".json");
        Path temp = folder.resolve(player + ".json.tmp");
        Path backup = folder.resolve(player + ".json.bak");
        Path backupTemp = folder.resolve(player + ".json.bak.tmp");

        try {
            writeProgressFile(temp.toFile());
            readProgressFile(temp.toFile());

            if (Files.exists(target) && isValidProgressFile(target.toFile())) {
                Files.copy(target, backupTemp, StandardCopyOption.REPLACE_EXISTING);
                replaceAtomically(backupTemp, backup);
            }

            replaceAtomically(temp, target);
        } finally {
            Files.deleteIfExists(temp);
            Files.deleteIfExists(backupTemp);
        }
    }

    private void writeProgressFile(File file) throws IOException {
        try (JsonWriter writer = new JsonWriter(new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)))) {
            writer.beginObject();
            for (Map.Entry<NamespacedKey, AdvancementProgress> entry : progressMap.entrySet()) {
                writer.name(entry.getKey().toString());
                writer.beginObject();
                writer.name("done").value(entry.getValue().done);
                writer.name("criteria");
                writer.beginObject();
                for (CriteriaProgress criterion : entry.getValue().criteria) {
                    writer.name(criterion.id).value(criterion.progress);
                }
                writer.endObject();
                writer.endObject();
            }
            writer.endObject();
        }
    }

    private static JsonObject readProgressFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) {
                throw new IllegalStateException("Advancement progress root is not a JSON object");
            }
            return root.getAsJsonObject();
        }
    }

    private static boolean isValidProgressFile(File file) {
        try {
            readProgressFile(file);
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static void recoverCorruptProgress(PlayerProgress progress, File file) {
        Path target = file.toPath();
        Path backup = target.resolveSibling(file.getName() + ".bak");
        Path corrupt = target.resolveSibling(file.getName() + ".corrupt-" + System.currentTimeMillis());

        try {
            Files.move(target, corrupt);
            SFAdvancements.logger().warning("Preserved corrupt advancement progress as " + corrupt.getFileName());
        } catch (IOException moveError) {
            SFAdvancements.logger().log(Level.WARNING,
                "Could not preserve corrupt advancement progress file " + file.getName(), moveError);
        }

        if (Files.exists(backup)) {
            try {
                progress.loadFromObject(readProgressFile(backup.toFile()));
                Path restoreTemp = target.resolveSibling(file.getName() + ".restore.tmp");
                try {
                    Files.copy(backup, restoreTemp, StandardCopyOption.REPLACE_EXISTING);
                    replaceAtomically(restoreTemp, target);
                } finally {
                    Files.deleteIfExists(restoreTemp);
                }
                SFAdvancements.logger().warning("Restored advancement progress for " + progress.player + " from backup");
                return;
            } catch (IOException | RuntimeException backupError) {
                SFAdvancements.logger().log(Level.WARNING,
                    "Backup advancement progress is also invalid for " + progress.player, backupError);
                progress.progressMap.clear();
            }
        }

        try {
            progress.save();
            SFAdvancements.logger().warning("Started fresh advancement progress for " + progress.player);
        } catch (IOException saveError) {
            SFAdvancements.logger().log(Level.SEVERE,
                "Could not create clean advancement progress for " + progress.player, saveError);
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * determines if a given advancement is completed for this player progress
     *
     * @param key the key of the advancement
     * @return if the advancement is completed
     */
    public synchronized boolean isCompleted(NamespacedKey key) {
        if (!progressMap.containsKey(key)) {
            return false;
        }
        AdvancementProgress prog = progressMap.get(key);
        return prog.done;
    }

    class AdvancementProgress {
        Advancement adv;
        boolean done = false;
        CriteriaProgress[] criteria;

        AdvancementProgress(NamespacedKey adv) {
            this(Utils.fromKey(adv));
        }

        AdvancementProgress(Advancement adv) {
            this.adv = adv;
            this.criteria = new CriteriaProgress[adv.getCriteria().length];
            for (int i = 0; i < adv.getCriteria().length; i++) {
                criteria[i] = new CriteriaProgress(adv.getCriteria()[i].getId());
            }
        }

        void updateDone() {
            for (CriteriaProgress criterion : criteria) {
                if (!criterion.done) {
                    return;
                }
            }
            this.done = true;

            adv.onComplete(Bukkit.getPlayer(player));
        }

        void loadFromObject(JsonObject object) {
            JsonElement doneElement = object.get("done");
            done = doneElement != null
                && doneElement.isJsonPrimitive()
                && doneElement.getAsJsonPrimitive().isBoolean()
                && doneElement.getAsBoolean();

            JsonElement criteriaElement = object.get("criteria");
            JsonObject jsonCriteria = criteriaElement != null && criteriaElement.isJsonObject()
                ? criteriaElement.getAsJsonObject()
                : new JsonObject();

            criteria = new CriteriaProgress[adv.getCriteria().length];
            int i = 0;
            for (Criterion criterion : adv.getCriteria()) {
                int progress = 0;
                JsonElement element = jsonCriteria.get(criterion.getId());
                if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    try {
                        progress = Math.max(0, element.getAsInt());
                    } catch (NumberFormatException ignored) {
                        progress = 0;
                    }
                }

                CriteriaProgress criteriaProgress = new CriteriaProgress(criterion.getId(), progress);
                criteriaProgress.done = progress >= criterion.getCount();
                criteria[i] = criteriaProgress;
                i++;
            }
        }
    }

    static class CriteriaProgress {
        String id;
        boolean done = false;
        //TODO make this easier to use so people can add their own criteria progress types like string
        int progress;

        CriteriaProgress(String id) {
            this(id, 0);
        }

        CriteriaProgress(String id, int progress) {
            this.id = id;
            this.progress = progress;
        }
    }
}
