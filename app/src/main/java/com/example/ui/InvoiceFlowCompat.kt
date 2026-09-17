package com.example.ui

import com.example.data.NoteItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first as flowFirst

suspend fun Flow<List<NoteItem>>.first(): List<NoteItem> = flowFirst()
