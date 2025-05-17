package com.movil.taller3.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.movil.taller3.R
import com.movil.taller3.models.User

class UserAdapter(
    private val users: List<User>,
    private val onTrackButtonClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageProfile: ImageView = itemView.findViewById(R.id.imageViewProfile)
        val textName: TextView = itemView.findViewById(R.id.textViewName)
        val buttonTrack: Button = itemView.findViewById(R.id.buttonTrack)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.textName.text = "${user.firstName} ${user.lastName}"

        // Load user profile image with Glide
        if (user.imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(user.imageUrl)
                .placeholder(R.drawable.baseline_add_reaction_24)
                .error(R.drawable.baseline_add_reaction_24)
                .circleCrop()
                .into(holder.imageProfile)
        } else {
            holder.imageProfile.setImageResource(R.drawable.baseline_add_reaction_24)
        }

        holder.buttonTrack.setOnClickListener {
            onTrackButtonClick(user)
        }
    }

    override fun getItemCount() = users.size
}