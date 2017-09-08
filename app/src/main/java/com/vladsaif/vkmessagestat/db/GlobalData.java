package com.vladsaif.vkmessagestat.db;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import com.vladsaif.vkmessagestat.utils.Easies;
import com.vladsaif.vkmessagestat.utils.Pair;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;

public class GlobalData {
    private static final String LOG_TAG = GlobalData.class.getSimpleName();
    private static final String filename = "usernames";
    private SparseArray<User> idToUsername = new SparseArray<>();

    public GlobalData(Context context) {
        ObjectInputStream inputStream = Easies.getObjectInputStream(context, filename);
        if (inputStream != null) {
            try {
                ArrayList<Pair<Integer, User>> serialized = (ArrayList<Pair<Integer, User>>) inputStream.readObject();
                for (Pair<Integer, User> pair : serialized) {
                    idToUsername.put(pair.first, pair.second);
                }
                inputStream.close();
            } catch (ClassNotFoundException cnfe) {
                Log.wtf(LOG_TAG, cnfe);
            } catch (IOException io) {
                Log.wtf(LOG_TAG, io);
            }
        }
    }

    public static final User unknown_user = new User(-1, "Неизвестный", 2);

    public User getUser(int dialog_id) {
        return idToUsername.get(dialog_id, unknown_user);
    }

    public void putUser(User user) {
        idToUsername.put(user.dialog_id, user);
    }

    public void serializeThis(Context context) {
        ArrayList<Pair<Integer, User>> serialized = new ArrayList<>();
        for (int i = 0; i < idToUsername.size(); ++i) {
            serialized.add(new Pair<>(idToUsername.keyAt(i), idToUsername.valueAt(i)));
        }
        Easies.serializeObject(serialized, filename, context);
    }

    public boolean contains(int dialog_id) {
        return idToUsername.get(dialog_id, null) != null;
    }

    public static class User implements Serializable {
        public static final int SEX_FEMALE = 1;
        public static final int SEX_MALE = 2;
        public int dialog_id;
        public String name;
        public int sex;

        public User(int dialog_id, String name, int sex) {
            this.sex = sex;
            this.dialog_id = dialog_id;
            this.name = name;
        }

    }
}
