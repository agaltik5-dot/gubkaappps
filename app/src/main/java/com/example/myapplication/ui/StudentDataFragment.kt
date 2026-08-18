package com.example.myapplication.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.example.myapplication.R
import com.google.android.material.imageview.ShapeableImageView
import java.io.File
import java.io.FileOutputStream

class StudentDataFragment : Fragment(R.layout.fragment_student_data) {

    private lateinit var ivAvatar: ShapeableImageView
    private lateinit var tvName: TextView
    private lateinit var tvGroup: TextView
    private lateinit var tvFaculty: TextView
    private lateinit var tvBirthDate: TextView

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveAndDisplayImage(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация по твоим ID из скриншота:
        ivAvatar = view.findViewById(R.id.iv_student_avatar)
        tvName = view.findViewById(R.id.tv_student_name)
        tvGroup = view.findViewById(R.id.tv_group_value)
        tvFaculty = view.findViewById(R.id.tv_faculty_value)
        tvBirthDate = view.findViewById(R.id.tv_birth_value)

        // Скрываем нижнее навигационное меню
        activity?.findViewById<View>(R.id.card_nav)?.visibility = View.GONE

        // Кнопка "Назад"
        view.findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Кнопка "Выбрать фото"
        view.findViewById<View>(R.id.btn_select_photo)?.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        // Кнопка "Изменить" (переход во фрагмент редактирования)
        val btnEdit = view.findViewById<View>(R.id.btn_edit_profile)
        btnEdit?.setOnClickListener {
            openEditProfile()
        }
    }

    fun openEditProfile() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, EditStudentDataFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<View>(R.id.card_nav)?.visibility = View.GONE
        loadUserData()
    }

    private fun loadUserData() {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        val firstName = prefs.getString("user_first_name", "Иван")
        val lastName = prefs.getString("user_last_name", "Иванов")
        tvName.text = "$lastName $firstName"

        tvGroup.text = prefs.getString("user_group", "ИВТ-221")
        tvFaculty.text = prefs.getString("user_faculty", "Информационные технологии")
        tvBirthDate.text = prefs.getString("user_birth_date", "27 мар. 2005 (19 лет)")

        // Загрузка фото
        val savedPath = prefs.getString("profile_avatar_path", null)
        if (!savedPath.isNullOrEmpty()) {
            val file = File(savedPath)
            if (file.exists()) {
                Glide.with(this)
                    .load(file)
                    .signature(ObjectKey(file.lastModified().toString()))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(ivAvatar)
            }
        }
    }

    private fun saveAndDisplayImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return
            val file = File(requireContext().filesDir, "user_avatar.jpg")
            val outputStream = FileOutputStream(file, false)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("profile_avatar_path", file.absolutePath) }

            Glide.with(this)
                .load(file)
                .signature(ObjectKey(file.lastModified().toString()))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(ivAvatar)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.findViewById<View>(R.id.card_nav)?.visibility = View.VISIBLE
    }
}