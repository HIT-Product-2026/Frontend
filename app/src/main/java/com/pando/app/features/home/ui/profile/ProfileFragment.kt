package com.pando.app.features.home.ui.profile

import android.app.DatePickerDialog
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.extensions.showShortToast
import com.pando.app.core.session.UserSession
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentProfileBinding
import com.pando.app.features.home.data.model.entity.CurrentUserProfile
import com.pando.app.features.home.data.model.entity.enumEntity.Gender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
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
    private var pendingDisplayName: String? = null
    private var pendingProfile: CurrentUserProfile? = null

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

        binding.saveButton.setOnClickListener {
            val displayName = binding.displayNameET.text.toString().trim()
            val phoneNumber = binding.phoneET.text.toString().trim()
            val birthday = binding.birthDateTV.text.toString().trim()
            val birthdayDate = validateProfileInput(phoneNumber, birthday)
                ?: return@setOnClickListener
            val gender = when (binding.genderTV.text.toString()) {
                "Nam" -> Gender.MALE
                "Nữ" -> Gender.FEMALE
                "Khác" -> Gender.OTHER
                else -> null
            }

            if (gender == null) {
                Toast.makeText(requireContext(), "Vui lòng chọn giới tính", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            pendingDisplayName = displayName
            pendingProfile = CurrentUserProfile(
                birthday = birthdayDate.toString(),
                gender = gender,
                phoneNumber = phoneNumber
            )

            profileViewModel.updateProfile(
                displayName = displayName,
                birthday = birthday,
                gender = gender,
                phoneNumber = phoneNumber
            )
        }

        binding.phoneET.doOnTextChanged { _, _, _, _ ->
            binding.phoneLayout.error = null
        }

        binding.btnEditAvatar.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_bottomSheetEditAvatarFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
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

                                val updatedDisplayName = pendingDisplayName
                                val updatedProfile = pendingProfile

                                if (updatedDisplayName != null && updatedProfile != null) {
                                    userSession.updateCurrentUser { user ->
                                        user.copy(
                                            displayName = updatedDisplayName,
                                            profile = updatedProfile
                                        )
                                    }
                                }

                                pendingDisplayName = null
                                pendingProfile = null
                                requireContext().showShortToast(R.string.profile_updated_success)
                                findNavController().navigateUp()
                            }

                            is UiState.Error -> {
                                binding.saveButton.isEnabled = true
                                binding.saveText.visibility = View.VISIBLE
                                binding.saveProgressBar.visibility = View.GONE
                                Toast.makeText(
                                    requireContext(),
                                    state.message,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
                launch {
                    profileViewModel.avatarResult.collect { state ->
                        when (state) {
                            is UiState.Loading -> {
                                binding.btnEditAvatar.isEnabled = false
                            }

                            is UiState.Success -> {
                                val avatarBytes = selectedAvatarFile
                                if (avatarBytes != null) {
                                    userSession.updateAvatar(selectedAvatarUri)
                                }

                                clearSelectedAvatarFile()
                                binding.btnEditAvatar.isEnabled = true
                                requireContext().showShortToast(R.string.avatar_updated_success)
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
    }

    private fun initDatePicker() {
        val dataSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            val selectedDate = LocalDate.of(year, month + 1, day)
            if (ProfileInputValidator.isFutureBirthday(selectedDate)) {
                binding.birthDateLayout.error = getString(R.string.birthday_in_future)
                return@OnDateSetListener
            }

            binding.birthDateLayout.error = null
            binding.birthDateTV.setText(
                ProfileInputValidator.formatDisplayBirthday(selectedDate),
                false
            )
        }

        binding.birthDateTV.setOnClickListener {
            binding.birthDateLayout.error = null
            val today = LocalDate.now()
            val initialDate = ProfileInputValidator
                .parseDisplayBirthday(binding.birthDateTV.text.toString())
                ?.takeUnless { ProfileInputValidator.isFutureBirthday(it) }
                ?: today
            val calendar = Calendar.getInstance()
            calendar.set(
                initialDate.year,
                initialDate.monthValue - 1,
                initialDate.dayOfMonth
            )

            datePickerDialog = DatePickerDialog(
                requireContext(),
                dataSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
            datePickerDialog.show()
        }
    }

    private fun validateProfileInput(phoneNumber: String, birthday: String): LocalDate? {
        binding.phoneLayout.error = null
        binding.birthDateLayout.error = null

        var isValid = true
        if (!ProfileInputValidator.isValidVietnamPhone(phoneNumber)) {
            binding.phoneLayout.error = getString(R.string.invalid_vietnam_phone)
            isValid = false
        }

        val birthdayDate = ProfileInputValidator.parseDisplayBirthday(birthday)
        when {
            birthday.isBlank() -> {
                binding.birthDateLayout.error = getString(R.string.birthday_required)
                isValid = false
            }

            birthdayDate == null -> {
                binding.birthDateLayout.error = getString(R.string.birthday_invalid)
                isValid = false
            }

            ProfileInputValidator.isFutureBirthday(birthdayDate) -> {
                binding.birthDateLayout.error = getString(R.string.birthday_in_future)
                isValid = false
            }
        }

        if (!isValid) {
            if (binding.phoneLayout.error != null) {
                binding.phoneET.requestFocus()
            }
            return null
        }

        return birthdayDate
    }

    private fun loadCurrentUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userSession.currentUser.collect { user ->
                    binding.profileIcon.loadAvatar(user?.avatar)
                    binding.displayNameET.setText(user?.displayName.orEmpty())

                    val profile = user?.profile
                    val birthday = profile?.birthday.orEmpty()
                    val birthdayParsed = runCatching { LocalDate.parse(birthday) }.getOrNull()
                        ?: ProfileInputValidator.parseDisplayBirthday(birthday)
                    val displayBirthday = birthdayParsed
                        ?.let(ProfileInputValidator::formatDisplayBirthday)
                        ?: birthday

                    binding.birthDateTV.setText(displayBirthday, false)
                    binding.phoneET.setText(profile?.phoneNumber.orEmpty())
                    binding.genderTV.setText(
                        when (profile?.gender) {
                            Gender.MALE -> "Nam"
                            Gender.FEMALE -> "Nữ"
                            Gender.OTHER -> "Khác"
                            null -> ""
                        },
                        false
                    )
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
