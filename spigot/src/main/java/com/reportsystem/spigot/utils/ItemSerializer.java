package com.reportsystem.spigot.utils;

import com.reportsystem.common.replay.ReplayObjectFilters;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class ItemSerializer {

    /**
     * ItemStack'i Base64 string'e serialize eder
     */
    public static String itemStackToBase64(ItemStack item) {
        if (item == null) return null;

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeObject(item);
            dataOutput.close();

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * ItemStack'i byte array'e serialize eder
     */
    public static byte[] itemStackToBytes(ItemStack item) {
        if (item == null) return null;

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeObject(item);
            dataOutput.close();

            return outputStream.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Base64 string'den ItemStack oluşturur (izin listeli filtreyle, H-22)
     */
    public static ItemStack itemStackFromBase64(String data) {
        if (data == null) return null;

        try (BukkitObjectInputStream dataInput =
                     new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
            dataInput.setObjectInputFilter(ReplayObjectFilters.bukkitItem());
            return (ItemStack) dataInput.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Byte array'den ItemStack oluşturur (izin listeli filtreyle, H-22)
     */
    public static ItemStack itemStackFromBytes(byte[] data) {
        if (data == null) return null;

        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            dataInput.setObjectInputFilter(ReplayObjectFilters.bukkitItem());
            return (ItemStack) dataInput.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Alias for itemStackToBase64 - serialize ItemStack to String
     */
    public static String serializeItemStack(ItemStack item) {
        return itemStackToBase64(item);
    }

    /**
     * Alias for itemStackFromBase64 - deserialize String to ItemStack
     */
    public static ItemStack deserializeItemStack(String data) {
        return itemStackFromBase64(data);
    }
}