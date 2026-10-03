package dev.dihclient.setting;

import com.google.gson.JsonElement;
import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
   private final String name;
   private final String description;
   private String legacyKey;
   private BooleanSupplier visibility = () -> true;
   protected final T defaultValue;
   protected T value;
   private Runnable onChange;

   protected Setting(String var1, String var2, T var3) {
      this.name = var1;
      this.description = var2 == null ? "" : var2;
      this.defaultValue = (T)var3;
      this.value = (T)var3;
   }

   public String name() {
      return this.name;
   }

   public String description() {
      return this.description;
   }

   public T get() {
      return this.value;
   }

   public void set(T var1) {
      Object var2 = this.sanitize((T)var1);
      if (var2 != null) {
         boolean var3 = !var2.equals(this.value);
         this.value = (T)var2;
         if (var3 && this.onChange != null) {
            this.onChange.run();
         }
      }
   }

   public void reset() {
      this.set(this.defaultValue);
   }

   public T defaultValue() {
      return this.defaultValue;
   }

   protected T sanitize(T var1) {
      return (T)var1;
   }

   public boolean isVisible() {
      return this.visibility.getAsBoolean();
   }

   public <S extends Setting<T>> S visibleWhen(BooleanSupplier var1) {
      this.visibility = var1;
      return (S)this;
   }

   public <S extends Setting<T>> S legacy(String var1) {
      this.legacyKey = var1;
      return (S)this;
   }

   public <S extends Setting<T>> S onChange(Runnable var1) {
      this.onChange = var1;
      return (S)this;
   }

   public String legacyKey() {
      return this.legacyKey;
   }

   public String id() {
      return this.name.toLowerCase().replace(' ', '_');
   }

   public abstract JsonElement toJson();

   public abstract void fromJson(JsonElement var1);

   public abstract void fromLegacyString(String var1);

   public abstract String displayValue();
}
