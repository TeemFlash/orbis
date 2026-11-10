package kz.semey.map;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import com.google.firebase.auth.*;
import com.google.firebase.database.*;
import java.time.ZoneId;
import java.util.*;

/** One shared room. Firebase listeners are attached only while the screen is visible. */
public class ChatActivity extends Activity {
  private FirebaseAuth auth;
  private DatabaseReference messages, connection;
  private Query query;
  private ValueEventListener listener, connectionListener;
  private FirebaseAuth.AuthStateListener authListener;
  private TextView status, empty;
  private EditText input;
  private ImageButton send, attach;
  private LinearLayout photoPreview;
  private ImageView preview;
  private String previewData;
  private final android.util.LruCache<String, android.graphics.Bitmap> photos =
      new android.util.LruCache<String, android.graphics.Bitmap>(8 * 1024 * 1024) {
        protected int sizeOf(String key, android.graphics.Bitmap bitmap) {
          return bitmap.getByteCount();
        }
      };
  private final Set<String> attemptedPhotos=new HashSet<>();
  private final Set<String> loadingPhotos = new HashSet<>();
  private final Set<String> failedPhotos = new HashSet<>();
  private static final java.util.concurrent.ExecutorService photoWorker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  private ListView list;
  private final ArrayList<Message> rows = new ArrayList<>();
  private MessageAdapter adapter;
  private boolean connected, started, redirected;
  private Session session;

  private static class Session {
    boolean pending, preparing;
    String photo;
    String pendingKey, error = "", restoreDraft = "";
    Runnable changed;

    void update() {
      if (changed != null) changed.run();
    }
  }

  private static class Message {
    String uid, name, text, key, photoId;
    Long createdAt;
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    FirebaseConfig.init();
    auth = FirebaseAuth.getInstance();
    if (auth.getCurrentUser() == null) {
      redirect();
      return;
    }
    Object retained = getLastNonConfigurationInstance();
    session = retained instanceof Session ? (Session) retained : new Session();
    messages = FirebaseDatabase.getInstance().getReference("messages");
    connection = FirebaseDatabase.getInstance().getReference(".info/connected");
    query = messages.orderByChild("createdAt").limitToLast(100);
    AppUi.bars(this, false);
    LinearLayout root = AppUi.column(this);
    root.setBackground(AppUi.wash());
    root.setPadding(d(20), d(12), d(20), d(10));
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(
        AppUi.text(this, "Chats", 30, AppUi.INK, true), new LinearLayout.LayoutParams(0, d(52), 1));
    ImageButton logout = AppUi.iconButton(this, R.drawable.ic_log_out, "Sign out", 0xAAFFFFFF);
    logout.setOnClickListener(
        v -> {
          if (session.pending) {
            status.setText("Wait for your message to finish sending.");
            return;
          }
          auth.signOut();
          redirect();
        });
    header.addView(logout, new LinearLayout.LayoutParams(d(48), d(48)));
    root.addView(header);
    LinearLayout room = new LinearLayout(this);
    room.setGravity(Gravity.CENTER_VERTICAL);
    room.setPadding(d(16), d(14), d(16), d(14));
    room.setBackground(AppUi.round(this, 0xBBFFFFFF, 24));
    ImageView avatar = new ImageView(this);
    avatar.setImageDrawable(AppUi.icon(this, R.drawable.ic_messages_square, 24, AppUi.INK));
    avatar.setPadding(d(12), d(12), d(12), d(12));
    avatar.setBackground(AppUi.round(this, 0xFF9DDCD2, 25));
    room.addView(avatar, new LinearLayout.LayoutParams(d(50), d(50)));
    LinearLayout info = AppUi.column(this);
    info.setPadding(d(12), 0, 0, 0);
    info.addView(AppUi.text(this, "Orbis lounge", 18, AppUi.INK, true));

    room.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(room, AppUi.row(this, 8));
    status = AppUi.text(this, "Connecting…", 12, 0xFF49646B, false);
    status.setPadding(d(4), d(10), d(4), d(8));
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    root.addView(status);
    FrameLayout conversation = new FrameLayout(this);
    root.addView(conversation, new LinearLayout.LayoutParams(-1, 0, 1));
    list = new ListView(this);
    list.setDivider(null);
    list.setDividerHeight(d(8));
    list.setSelector(android.R.color.transparent);
    list.setStackFromBottom(true);
    list.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
    list.setPadding(0, d(8), 0, d(8));
    list.setClipToPadding(false);
    adapter = new MessageAdapter();
    list.setAdapter(adapter);
    conversation.addView(list, new FrameLayout.LayoutParams(-1, -1));
    empty = AppUi.text(this, "No messages", 17, 0xFF587479, false);
    empty.setGravity(Gravity.CENTER);
    conversation.addView(empty, new FrameLayout.LayoutParams(-1, -1));
    list.setEmptyView(empty);
    photoPreview = new LinearLayout(this);
    photoPreview.setGravity(Gravity.CENTER_VERTICAL);
    preview = new ImageView(this);
    preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
    photoPreview.addView(preview, new LinearLayout.LayoutParams(d(64), d(64)));
    TextView photoLabel = AppUi.text(this, "Photo", 12, AppUi.INK, false);
    photoLabel.setPadding(d(10), 0, 0, 0);
    photoPreview.addView(photoLabel, new LinearLayout.LayoutParams(0, -2, 1));
    ImageButton remove =
        AppUi.iconButton(this, R.drawable.ic_x, "Remove attached photo", 0xAAFFFFFF);
    remove.setOnClickListener(
        v -> {
          if (!session.pending && !session.preparing) {
            session.photo = null;
            refresh();
          }
        });
    photoPreview.addView(remove, new LinearLayout.LayoutParams(d(48), d(48)));
    root.addView(photoPreview, AppUi.row(this, 8));
    LinearLayout composer = new LinearLayout(this);
    composer.setGravity(Gravity.CENTER_VERTICAL);
    composer.setPadding(d(6), d(6), d(6), d(6));
    composer.setBackground(AppUi.round(this, Color.WHITE, 28));
    attach = AppUi.iconButton(this, R.drawable.ic_image_plus, "Attach photo", Color.TRANSPARENT);
    attach.setOnClickListener(
        v -> {
          Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT);
          choose.setType("image/*");
          choose.addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(choose, 31);
        });
    composer.addView(attach, new LinearLayout.LayoutParams(d(48), d(48)));
    input = new EditText(this);
    input.setId(R.id.chat_input);
    input.setHint("Message…");
    input.setTextSize(15);
    input.setTextColor(AppUi.INK);
    input.setHintTextColor(0xFF6D8083);
    input.setBackgroundColor(Color.TRANSPARENT);
    input.setMaxLines(4);
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1000)});
    input.setPadding(d(12), d(8), d(8), d(8));
    composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
    send = AppUi.iconButton(this, R.drawable.ic_send, "Send message", 0xFFB7E7DB);
    send.setContentDescription("Send message");
    send.setOnClickListener(v -> sendMessage());
    composer.addView(send, new LinearLayout.LayoutParams(d(48), d(48)));
    root.addView(composer, AppUi.row(this, 8));
    root.addView(AppNavigation.create(this, "chats", () -> !waitForPendingMessage()),
        new LinearLayout.LayoutParams(-1, d(64)));
    setContentView(root);
    listener =
        new ValueEventListener() {
          public void onDataChange(DataSnapshot data) {
            boolean bottom = rows.isEmpty() || list.getLastVisiblePosition() >= rows.size() - 2;
            rows.clear();
            for (DataSnapshot child : data.getChildren())
              try {
                Message m = new Message();
                m.key = child.getKey();
                m.photoId = child.child("photoId").getValue(String.class);
                m.uid = child.child("uid").getValue(String.class);
                m.name = child.child("name").getValue(String.class);
                m.text = child.child("text").getValue(String.class);
                m.createdAt = child.child("createdAt").getValue(Long.class);
                if (m.uid != null && m.name != null && m.text != null) rows.add(m);
              } catch (DatabaseException ignored) {
              }
            adapter.notifyDataSetChanged();
            if (bottom && !rows.isEmpty()) list.setSelection(rows.size() - 1);
            refresh();
          }

          public void onCancelled(DatabaseError e) {
            session.error =
                e.getCode() == DatabaseError.PERMISSION_DENIED
                    ? "Chat access denied. Check the database rules."
                    : "Could not load messages. Reopen Chats to retry.";
            refresh();
          }
        };
    connectionListener =
        new ValueEventListener() {
          public void onDataChange(DataSnapshot d) {
            connected = Boolean.TRUE.equals(d.getValue(Boolean.class));
            refresh();
          }

          public void onCancelled(DatabaseError e) {
            connected = false;
            refresh();
          }
        };
    authListener =
        a -> {
          if (a.getCurrentUser() == null) redirect();
        };
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != 31 || result != RESULT_OK || data == null || data.getData() == null) return;
    Session target = session;
    target.preparing = true;
    target.error = "";
    refresh();
    android.content.ContentResolver resolver = getApplicationContext().getContentResolver();
    android.net.Uri uri = data.getData();
    photoWorker.execute(
        () -> {
          String encoded = null;
          try {
            encoded = PhotoCodec.encode(resolver, uri);
          } catch (Exception | OutOfMemoryError e) {
          }
          final String ready = encoded;
          new android.os.Handler(android.os.Looper.getMainLooper())
              .post(
                  () -> {
                    target.preparing = false;
                    if (ready == null)
                      target.error = "Could not open this photo. Choose another image.";
                    else target.photo = ready;
                    target.update();
                  });
        });
  }

  private void loadPhoto(String id) {
    if (loadingPhotos.contains(id) || failedPhotos.contains(id)) return;
    if(attemptedPhotos.contains(id)){failedPhotos.add(id);return;}
    attemptedPhotos.add(id);
    loadingPhotos.add(id);
    FirebaseDatabase.getInstance()
        .getReference("photos")
        .child(id)
        .child("data")
        .get()
        .addOnCompleteListener(
            task -> {
              if (isDestroyed()) return;
              String encoded = task.isSuccessful() ? task.getResult().getValue(String.class) : null;
              photoWorker.execute(
                  () -> {
                    android.graphics.Bitmap bitmap = PhotoCodec.decode(encoded);
                    runOnUiThread(
                        () -> {
                          loadingPhotos.remove(id);
                          if (isDestroyed()) return;
                          if (bitmap == null) failedPhotos.add(id);
                          else photos.put(id, bitmap);
                          adapter.notifyDataSetChanged();
                        });
                  });
            });
  }

  private int d(int n) {
    return AppUi.dp(this, n);
  }

  private void refresh() {
    if (isDestroyed() || session == null) return;
    send.setEnabled(connected && !session.pending && !session.preparing);
    attach.setEnabled(!session.pending && !session.preparing);
    photoPreview.setVisibility(session.photo != null ? View.VISIBLE : View.GONE);
    if (!java.util.Objects.equals(previewData, session.photo)) {
      previewData = session.photo;
      preview.setImageBitmap(PhotoCodec.decode(session.photo));
    }
    input.setEnabled(!session.pending);
    if (!session.restoreDraft.isEmpty() && input.getText().length() == 0) {
      input.setText(session.restoreDraft);
      session.restoreDraft = "";
    }
    status.setText(
        session.preparing
            ? "Preparing photo…"
            : !session.error.isEmpty()
                ? session.error
                : session.pending
                    ? (connected
                        ? "Sending…"
                        : "Waiting for connection · message not delivered yet")
                    : connected ? "" : "Offline · reconnect to send");
    status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
    adapter.notifyDataSetChanged();
  }

  private void sendMessage() {
    if (session.pending || session.preparing) return;
    String text = input.getText().toString().trim();
    String problem =
        session.photo != null && text.isEmpty() ? null : ChatValidation.messageError(text);
    if (problem != null) {
      input.setError(problem);
      return;
    }
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) {
      redirect();
      return;
    }
    if (!connected) {
      status.setText("You're offline. Reconnect to send.");
      return;
    }
    String name = user.getDisplayName();
    if (name == null || name.trim().isEmpty()) name = "Explorer " + user.getUid().substring(0, 6);
    name = name.trim();
    if (name.length() > 40) name = name.substring(0, 40);
    DatabaseReference next = messages.push();
    final Session sending = session;
    sending.pending = true;
    sending.pendingKey = next.getKey();
    sending.error = "";
    HashMap<String, Object> value = new HashMap<>();
    value.put("uid", user.getUid());
    value.put("name", name);
    value.put("text", text);
    value.put("createdAt", ServerValue.TIMESTAMP);
    final String photo = sending.photo;
    Map<String, Object> updates = new HashMap<>();
    if (photo != null) {
      value.put("photoId", next.getKey());
      Map<String, Object> image = new HashMap<>();
      image.put("uid", user.getUid());
      image.put("data", photo);
      updates.put("photos/" + next.getKey(), image);
    }
    updates.put("messages/" + next.getKey(), value);
    input.setText("");
    refresh();
    FirebaseDatabase.getInstance()
        .getReference()
        .updateChildren(
            updates,
            (error, ref) -> {
              sending.pending = false;
              sending.pendingKey = null;
              if (error != null) {
                sending.restoreDraft = text;
                sending.error =
                    error.getCode() == DatabaseError.PERMISSION_DENIED
                        ? "Message rejected. Check the database rules."
                        : "Message was not sent. Try again.";
              } else {
                sending.error = "";
                sending.photo = null;
              }
              sending.update();
            });
  }

  private boolean waitForPendingMessage() {
    if (session != null && session.pending) {
      status.setText("Wait for your message to finish sending.");
      return true;
    }
    return false;
  }

  @Override
  public void onBackPressed() {
    if (!waitForPendingMessage()) super.onBackPressed();
  }

  private void redirect() {
    if (redirected) return;
    redirected = true;
    startActivity(new Intent(this, AuthActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    finish();
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (session == null) return;
    started = true;
    session.changed = () -> refresh();
    auth.addAuthStateListener(authListener);
    query.addValueEventListener(listener);
    connection.addValueEventListener(connectionListener);
    refresh();
  }

  @Override
  protected void onStop() {
    if (session != null) {
      started = false;
      session.changed = null;
      connected = false;
      query.removeEventListener(listener);
      connection.removeEventListener(connectionListener);
      auth.removeAuthStateListener(authListener);
    }
    super.onStop();
  }

  @Override
  public Object onRetainNonConfigurationInstance() {
    return session;
  }

  private class MessageAdapter extends BaseAdapter {
    public int getCount() {
      return rows.size();
    }

    public Object getItem(int p) {
      return rows.get(p);
    }

    public long getItemId(int p) {
      return p;
    }

    public View getView(int position, View reused, ViewGroup parent) {
      Message m = rows.get(position);
      FirebaseUser me = auth.getCurrentUser();
      boolean mine = me != null && me.getUid().equals(m.uid);
      LinearLayout wrapper = AppUi.column(ChatActivity.this);
      String separator =
          ChatDates.separatorLabel(
              messageTimestamps(), position, ZoneId.systemDefault(), Locale.getDefault());
      if (separator != null) {
        TextView date = AppUi.text(ChatActivity.this, separator, 12, 0xFF49646B, true);
        date.setGravity(Gravity.CENTER);
        date.setPadding(0, d(9), 0, d(9));
        wrapper.addView(date, new LinearLayout.LayoutParams(-1, -2));
      }
      LinearLayout messageRow = new LinearLayout(ChatActivity.this);
      messageRow.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
      messageRow.setPadding(mine ? d(36) : 0, 0, mine ? 0 : d(36), 0);
      LinearLayout bubble = AppUi.column(ChatActivity.this);
      bubble.setPadding(d(15), d(11), d(15), d(10));
      bubble.setBackground(AppUi.round(ChatActivity.this, mine ? 0xFFD8F4E9 : Color.WHITE, 20));
      bubble.addView(AppUi.text(ChatActivity.this, mine ? "You" : m.name, 11, 0xFF55736E, true));
      TextView body = AppUi.text(ChatActivity.this, m.text, 15, AppUi.INK, false);
      body.setPadding(0, d(4), 0, d(5));
      if (m.photoId != null && m.photoId.equals(m.key)) {
        ImageView picture = new ImageView(ChatActivity.this);
        picture.setContentDescription("Photo from " + m.name);
        picture.setBackground(AppUi.round(ChatActivity.this, 0xFFE4EFEB, 12));
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.graphics.Bitmap cached = photos.get(m.photoId);
        if (cached != null) picture.setImageBitmap(cached);
        else {
          picture.setImageDrawable(
              AppUi.icon(ChatActivity.this, R.drawable.ic_image_plus, 24, 0xFF597D80));
          loadPhoto(m.photoId);
        }
        picture.setOnClickListener(
            v -> {
              android.graphics.Bitmap image = photos.get(m.photoId);
              if (image == null) {
                failedPhotos.remove(m.photoId);
                attemptedPhotos.remove(m.photoId);
                loadPhoto(m.photoId);
                return;
              }
              ImageView large = new ImageView(ChatActivity.this);
              large.setImageBitmap(image);
              large.setAdjustViewBounds(true);
              new android.app.AlertDialog.Builder(ChatActivity.this)
                  .setView(large)
                  .setPositiveButton("Close", null)
                  .show();
            });
        bubble.addView(picture, new LinearLayout.LayoutParams(d(210), d(160)));
        if (failedPhotos.contains(m.photoId))
          bubble.addView(
              AppUi.text(
                  ChatActivity.this, "Photo unavailable · tap to retry", 11, 0xFF9B3434, false));
      }
      if (!m.text.isEmpty()) bubble.addView(body);
      boolean pending = Objects.equals(m.key, session.pendingKey);
      ZoneId zone = ZoneId.systemDefault();
      Locale locale = Locale.getDefault();
      TextView time =
          AppUi.text(
              ChatActivity.this,
              ChatDates.timeLabel(m.createdAt, pending, zone, locale),
              10,
              0xFF657B77,
              false);
      time.setGravity(Gravity.RIGHT);
      if (!pending && ChatDates.isValid(m.createdAt))
        time.setContentDescription(
            ChatDates.dateLabel(m.createdAt, zone, locale)
                + ", "
                + ChatDates.timeLabel(m.createdAt, false, zone, locale));
      bubble.addView(time);
      messageRow.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
      wrapper.addView(messageRow, new LinearLayout.LayoutParams(-1, -2));
      return wrapper;
    }

    private List<Long> messageTimestamps() {
      ArrayList<Long> timestamps = new ArrayList<>(rows.size());
      for (Message row : rows)
        timestamps.add(Objects.equals(row.key, session.pendingKey) ? null : row.createdAt);
      return timestamps;
    }
  }
}
