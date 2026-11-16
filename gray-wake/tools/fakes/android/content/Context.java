package android.content;
public final class Context {
    public boolean granted = true;
    public final ContentResolver resolver = new ContentResolver();
    public Context getApplicationContext() { return this; }
    public int checkSelfPermission(String permission) { return granted ? 0 : -1; }
    public ContentResolver getContentResolver() { return resolver; }
}
