package com.example.lab5_starter;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements CityDialogFragment.CityDialogListener {

    private Button addCityButton;
    private ListView cityListView;
    private ArrayList<City> cityArrayList;
    private ArrayAdapter<City> cityArrayAdapter;
    private CollectionReference citiesRef;
    private FirebaseFirestore db;

    // Variables for delete city feature
    private boolean deleteMode = false;
    private Button deleteCityButton;
    private float swipeStartX;
    private float swipeStartY;
    private View swipedView;
    private int swipedPosition = -1;
    private boolean isSwiping = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set views
        addCityButton = findViewById(R.id.buttonAddCity);
        deleteCityButton = findViewById(R.id.buttonDeleteCity);  // Added for delete feature
        cityListView = findViewById(R.id.listviewCities);

        // create city array
        cityArrayList = new ArrayList<>();
        cityArrayAdapter = new CityArrayAdapter(this, cityArrayList);
        cityListView.setAdapter(cityArrayAdapter);

        //addDummyData();
        db = FirebaseFirestore.getInstance();
        citiesRef = db.collection("cities");

        citiesRef.addSnapshotListener((QuerySnapshot value, FirebaseFirestoreException error) -> {
            if (error != null) {
                Log.e("Firestore", error.toString());
            }
            if (value != null && !value.isEmpty()) {
                cityArrayList.clear();
                for (QueryDocumentSnapshot snapshot : value) {
                    String name = snapshot.getString("name");
                    String province = snapshot.getString("province");

                    cityArrayList.add(new City(name, province));
                }
                cityArrayAdapter.notifyDataSetChanged();
            }
        });

        // set listeners
        addCityButton.setOnClickListener(view -> {
            CityDialogFragment cityDialogFragment = new CityDialogFragment();
            cityDialogFragment.show(getSupportFragmentManager(),"Add City");
        });

        // DELETE CITY button listener - Added for delete feature
        deleteCityButton.setOnClickListener(view -> {
            if (!deleteMode) {
                // Enter delete mode
                deleteMode = true;
                deleteCityButton.setText("CANCEL DELETE");
                Toast.makeText(this, "Click a city to delete it", Toast.LENGTH_SHORT).show();
            } else {
                // Exit delete mode
                deleteMode = false;
                deleteCityButton.setText("DELETE CITY");
            }
        });

        cityListView.setOnItemClickListener((adapterView, view, i, l) -> {
            if (deleteMode) {
                // DELETE MODE: Show delete confirmation - Added for delete feature
                City city = cityArrayList.get(i);
                new AlertDialog.Builder(this)
                        .setTitle("Delete City")
                        .setMessage("Delete " + city.getName() + "?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            deleteCityFromFirestore(i);
                            // Exit delete mode after deletion
                            deleteMode = false;
                            deleteCityButton.setText("DELETE CITY");
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            // Stay in delete mode
                        })
                        .show();
            } else {
                City city = cityArrayAdapter.getItem(i);
                CityDialogFragment cityDialogFragment = CityDialogFragment.newInstance(city);
                cityDialogFragment.show(getSupportFragmentManager(),"City Details");
            }
        });

        // Setup swipe to delete
        setupSwipeToDelete();
    }

    // Swipe to delete functionality
    @SuppressLint("ClickableViewAccessibility")
    private void setupSwipeToDelete() {
        cityListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Record where touch started
                        swipeStartX = event.getX();
                        swipeStartY = event.getY();
                        isSwiping = false;

                        // Find which list item was touched
                        swipedPosition = cityListView.pointToPosition((int) swipeStartX, (int) swipeStartY);
                        if (swipedPosition != ListView.INVALID_POSITION) {
                            // Get the actual view of the list item
                            int firstVisiblePosition = cityListView.getFirstVisiblePosition();
                            swipedView = cityListView.getChildAt(swipedPosition - firstVisiblePosition);
                        }
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (swipedView != null) {
                            // Calculate how far finger moved
                            float diffX = event.getX() - swipeStartX;
                            float diffY = event.getY() - swipeStartY;

                            // Check if this is a horizontal swipe
                            if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 20) {
                                isSwiping = true;
                                // Move the list item with the finger
                                swipedView.setTranslationX(diffX);
                                return true;
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        if (swipedView != null && isSwiping) {
                            float diffX = event.getX() - swipeStartX;

                            // If swiped far enough, show delete dialog
                            if (Math.abs(diffX) > 150) {
                                showDeleteDialog(swipedPosition);
                            }

                            // Animate back to original position
                            swipedView.animate()
                                    .translationX(0)
                                    .setDuration(200)
                                    .start();

                            // Reset variables
                            swipedView = null;
                            swipedPosition = -1;
                            isSwiping = false;

                            return true;
                        }
                        break;
                }

                return false;
            }
        });
    }

    // Show delete confirmation dialog - Added for delete feature
    private void showDeleteDialog(int position) {
        City city = cityArrayList.get(position);

        new AlertDialog.Builder(this)
                .setTitle("Delete City")
                .setMessage("Delete " + city.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteCityFromFirestore(position);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Delete city from Firestore - Added for delete feature
    private void deleteCityFromFirestore(int position) {
        if (position >= 0 && position < cityArrayList.size()) {
            City cityToDelete = cityArrayList.get(position);

            DocumentReference docRef = citiesRef.document(cityToDelete.getName());
            docRef.delete()
                    .addOnSuccessListener(aVoid -> {
                        Log.d("Firestore", "City deleted: " + cityToDelete.getName());
                        Toast.makeText(MainActivity.this,
                                "Deleted " + cityToDelete.getName(), Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.w("Firestore", "Error deleting city", e);
                        Toast.makeText(MainActivity.this,
                                "Failed to delete from cloud", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    @Override
    public void updateCity(City city, String title, String year) {
        city.setName(title);
        city.setProvince(year);
        cityArrayAdapter.notifyDataSetChanged();

        // Updating the database using delete + addition
        DocumentReference docRef = citiesRef.document(city.getName());
        docRef.set(city);
    }

    @Override
    public void addCity(City city) {
        cityArrayList.add(city);
        cityArrayAdapter.notifyDataSetChanged();

        DocumentReference docRef = citiesRef.document(city.getName());
        docRef.set(city);
    }

    public void addDummyData(){
        City m1 = new City("Edmonton", "AB");
        City m2 = new City("Vancouver", "BC");
        cityArrayList.add(m1);
        cityArrayList.add(m2);
        cityArrayAdapter.notifyDataSetChanged();
    }
}