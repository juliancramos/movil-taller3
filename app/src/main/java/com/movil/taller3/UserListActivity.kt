package com.movil.taller3

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.movil.taller3.adapters.UserAdapter
import com.movil.taller3.databinding.ActivityUserListBinding
import com.movil.taller3.models.User

class UserListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserListBinding
    private lateinit var userAdapter: UserAdapter
    private val availableUsers = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerViewUsers.layoutManager = LinearLayoutManager(this)
        userAdapter = UserAdapter(availableUsers) { user ->
            // Launch tracking activity when "Track" button is clicked
            val intent = Intent(this, AvailableUserActivity::class.java)
            intent.putExtra("USER_ID", user.idNumber)
            intent.putExtra("USER_NAME", "${user.firstName} ${user.lastName}")
            startActivity(intent)
        }
        binding.recyclerViewUsers.adapter = userAdapter

        loadAvailableUsers()

        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun loadAvailableUsers() {
        val database = FirebaseDatabase.getInstance()
        val usersRef = database.getReference("users")
        val currentUserID = FirebaseAuth.getInstance().currentUser?.uid

        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                availableUsers.clear()
                for (userSnapshot in snapshot.children) {
                    val user = userSnapshot.getValue(User::class.java)
                    // Only add user if available and not the current user
                    if (user != null && user.available && userSnapshot.key != currentUserID) {
                        // Include user ID in the model
                        availableUsers.add(user)
                    }
                }
                userAdapter.notifyDataSetChanged()

                // Update UI if no users are available
                if (availableUsers.isEmpty()) {
                    binding.tvNoUsers.visibility = View.VISIBLE
                } else {
                    binding.tvNoUsers.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("UsersListActivity", "Error loading users", error.toException())
            }
        })
    }
}