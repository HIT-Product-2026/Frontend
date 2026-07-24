package com.pando.app.features.home.ui.profile

import android.app.DatePickerDialog
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.session.UserSession
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentProfileBinding
import com.pando.app.features.home.data.model.entity.enumEntity.Gender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : BaseFragment<FragmentProfileBinding>(FragmentProfileBinding::inflate) {
    private lateinit var datePickerDialog: DatePickerDialog

    @Inject
    lateinit var userSession: UserSession
    private val profileViewModel: ProfileViewModel by viewModels()

    private var selectedAvatarUri: Uri? = null
    private var selectedAvatarFile: File? = null

    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) {
            return@registerForActivityResult
        }

        val avatarFile = uriToFile(uri)
        if (avatarFile == null) {
            Toast.makeText(requireContext(), "Không thể đọc ảnh đã chọn", Toast.LENGTH_SHORT).show()

            return@registerForActivityResult
        }

        selectedAvatarUri = uri
        selectedAvatarFile = avatarFile
        profileViewModel.uploadAvatar(avatarFile)
    }

    override fun initData() {
    }

    override fun initView() {
        loadCurrentUser()

        val genders = resources.getStringArray(R.array.gioi_tinh)
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_gender_item, genders)
        binding.genderTV.setAdapter(arrayAdapter)

        initDatePicker()
    }

    override fun initActionView() {
        parentFragmentManager.setFragmentResultListener(
            BottomSheetEditAvatarFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val action = bundle.getString(
                BottomSheetEditAvatarFragment.RESULT_KEY
            )

            when (action) {
                BottomSheetEditAvatarFragment.ACTION_CHOOSE_IMAGE -> {
                    openGallery()
                }
            }
        }

        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        var displayName = binding.displayNameET.text.toString()
        var phoneNumber: String
        var birthday: String
        var gender: String

        binding.saveButton.setOnClickListener {
            displayName = binding.displayNameET.text.toString()
            phoneNumber = binding.phoneET.text.toString()
            birthday = binding.birthDateTV.text.toString()
            gender = binding.genderTV.text.toString()

            profileViewModel.updateDisplayName(displayName)
            if (gender != "Giới tính") {
                when (gender) {
                    "Nam" -> {
                        profileViewModel.updateProfile(birthday, Gender.MALE, phoneNumber)
                    }

                    "Nữ" -> {
                        profileViewModel.updateProfile(birthday, Gender.FEMALE, phoneNumber)
                    }

                    else -> {
                        profileViewModel.updateProfile(birthday, Gender.OTHER, phoneNumber)
                    }
                }
            }
        }

        binding.btnEditAvatar.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_bottomSheetEditAvatarFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Idle -> {}

                        is UiState.Loading -> {
                            binding.saveButton.isEnabled = false
                            binding.saveText.visibility = View.GONE
                            binding.saveProgressBar.visibility = View.VISIBLE
                        }

                        is UiState.Success -> {
                            binding.saveButton.isEnabled = true
                            binding.saveText.visibility = View.VISIBLE
                            binding.saveProgressBar.visibility = View.GONE

                            userSession.updateCurrentUser { user ->
                                user.copy(
                                    displayName = displayName
                                )
                            }

                            findNavController().navigateUp()
                        }

                        is UiState.Error -> {
                            binding.saveButton.isEnabled = true
                            binding.saveText.visibility = View.VISIBLE
                            binding.saveProgressBar.visibility = View.GONE
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.avatarResult.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.btnEditAvatar.isEnabled = false
                        }

                        is UiState.Success -> {
                            val avatarBytes = selectedAvatarFile?.let(::fileToByteArray)
                            if (avatarBytes != null) {
                                userSession.updateAvatar(avatarBytes)
                            }

                            clearSelectedAvatarFile()
                            binding.btnEditAvatar.isEnabled = true
                            profileViewModel.clearAvatarResult()
                        }

                        is UiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT)
                                .show()

                            clearSelectedAvatarFile()
                            binding.btnEditAvatar.isEnabled = true
                            profileViewModel.clearAvatarResult()
                        }

                        is UiState.Idle -> {
                            binding.btnEditAvatar.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    fun initDatePicker() {
        val dataSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            val formattedDay = "%02d".format(day)
            val formattedMonth = "%02d".format(month + 1)

            val date: String = makeDateString(formattedDay, formattedMonth, year)

            binding.birthDateTV.setText(date)
        }

        binding.birthDateTV.setOnClickListener {
            val calendar = Calendar.getInstance()

            datePickerDialog = DatePickerDialog(
                requireContext(),
                dataSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            datePickerDialog.show()
        }
    }

    private fun makeDateString(day: String, month: String, year: Int): String {
        return "$day/$month/$year"
    }

    private fun loadCurrentUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userSession.currentUser.collect { user ->
                    binding.profileIcon.loadAvatar(user?.avatar)
                    binding.displayNameET.setText(user?.displayName.orEmpty())
                }
            }
        }
    }

    private fun openGallery() {
        pickAvatarLauncher.launch(
            PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = requireContext()
                .contentResolver
                .openInputStream(uri)
                ?: return null

            val tempFile = File.createTempFile(
                "avatar_",
                ".jpg",
                requireContext().cacheDir
            )

            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            tempFile
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Không thể chuyển Uri thành File", e)
            null
        }
    }

    private fun fileToByteArray(file: File): ByteArray? {
        return try {
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    private fun clearSelectedAvatarFile() {
        selectedAvatarFile?.let { file ->
            if (file.exists() && !file.delete()) {
                Log.w("ProfileFragment", "Không thể giải phóng file cache: ${file.absolutePath}")
            }
        }

        selectedAvatarFile = null
        selectedAvatarUri = null
    }
}
