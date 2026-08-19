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

    // Переменные для телефона и почты
    private var tvPhone: TextView? = null
    private var tvEmail: TextView? = null

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveAndDisplayImage(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация элементов экрана
        ivAvatar = view.findViewById(R.id.iv_student_avatar)
        tvName = view.findViewById(R.id.tv_student_name)
        tvGroup = view.findViewById(R.id.tv_group_value)
        tvFaculty = view.findViewById(R.id.tv_faculty_value)
        tvBirthDate = view.findViewById(R.id.tv_birth_value)

        // Инициализация полей Контактов (проверьте соответствие ID в вашем fragment_student_data.xml)
        tvPhone = view.findViewById(R.id.tv_phone_value)
            ?: view.findViewById(R.id.tv_phone_value)
        tvEmail = view.findViewById(R.id.tv_email_value)
            ?: view.findViewById(R.id.tv_email_value)

        // Скрываем нижнее навигационное меню
        activity?.findViewById<View>(R.id.card_nav)?.visibility = View.GONE

        // Подписка на событие сохранения из EditStudentDataFragment
        parentFragmentManager.setFragmentResultListener("student_data_updated", viewLifecycleOwner) { _, _ ->
            loadUserData()
        }

        // Кнопка "Назад"
        view.findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Кнопка "Выбрать фото"
        view.findViewById<View>(R.id.btn_select_photo)?.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        // Кнопка перехода к редактированию
        view.findViewById<View>(R.id.btn_edit_profile)?.setOnClickListener {
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

        val firstName = prefs.getString("user_first_name", "Иван").orEmpty()
        val lastName = prefs.getString("user_last_name", "Иванов").orEmpty()

        tvName.text = "$lastName $firstName".trim()
        tvGroup.text = prefs.getString("user_group", "МР-24-10")
        tvFaculty.text = prefs.getString("user_faculty", "ФИМ")
        tvBirthDate.text = prefs.getString("user_birth_date", "27.03.2005")

        // Вывод телефона и почты
        tvPhone?.text = prefs.getString("user_phone", "+79787433781")
        tvEmail?.text = prefs.getString("user_email", "ivan@univ.ru")

        // Загрузка фото аватарки
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