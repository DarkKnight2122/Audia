package com.oakiha.audia.presentation.viewmodel

import com.oakiha.audia.data.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages queue shuffle state.
 * Extracted from PlayerViewModel to improve modularity.
 * 
 * This class handles the original queue order for shuffle/unshuffle operations.
 */
@Singleton
class QueueStateHolder @Inject constructor() {
    
    // Original queue order before shuffle (for restoring when unshuffling)
    private var _originalQueueOrder: List<Track> = emptyList()
    val originalQueueOrder: List<Track> get() = _originalQueueOrder
    
    // Original queue name before shuffle
    private var _originalQueueName: String = "None"
    val originalQueueName: String get() = _originalQueueName
    
    /**
     * Store the original queue state before shuffling.
     */
    fun saveOriginalQueueState(queue: List<Track>, queueName: String) {
        _originalQueueOrder = queue.toList()
        _originalQueueName = queueName
    }
    
    /**
     * Set original queue order (for updates during playback).
     */
    fun setOriginalQueueOrder(queue: List<Track>) {
        _originalQueueOrder = queue.toList()
    }
    
    /**
     * Check if original queue is empty.
     */
    fun hasOriginalQueue(): Boolean = _originalQueueOrder.isNotEmpty()
    
    /**
     * Get the original queue for restoring after unshuffle.
     */
    fun getOriginalQueueForRestore(): List<Track> = _originalQueueOrder.toList()
    
    /**
     * Clear the original queue state (e.g., when queue is cleared).
     */
    fun clearOriginalQueue() {
        _originalQueueOrder = emptyList()
        _originalQueueName = "None"
    }
    
    /**
     * Create a shuffled version of a queue, keeping the current Track at the start.
     */
    fun createShuffledQueue(
        currentQueue: List<Track>,
        currentTrackId: String?
    ): List<Track> {
        if (currentQueue.isEmpty()) return emptyList()
        
        val currentTrack = currentQueue.find { it.id == currentTrackId }
        val otherTracks = currentQueue.filter { it.id != currentTrackId }.shuffled()
        
        return if (currentTrack != null) {
            listOf(currentTrack) + otherTracks
        } else {
            otherTracks.shuffled()
        }
    }
    /**
     * Prepares a list for shuffled playback.
     * 1. Saves original queue.
     * 2. Picks a random start Track.
     * 3. Creates a shuffled list starting with that Track.
     */
    fun prepareShuffledQueue(Tracks: List<Track>, queueName: String): Pair<List<Track>, Track>? {
        if (Tracks.isEmpty()) return null
        
        val startTrack = Tracks.random()
        saveOriginalQueueState(Tracks, queueName)
        
        val otherTracks = Tracks.filter { it.id != startTrack.id }.shuffled()
        val shuffledQueue = listOf(startTrack) + otherTracks
        
        return Pair(shuffledQueue, startTrack)
    }

    /**
     * Prepares a list for shuffled playback with a specific start Track.
     */
    fun prepareShuffledQueueWithStart(Tracks: List<Track>, startTrack: Track, queueName: String): List<Track> {
         saveOriginalQueueState(Tracks, queueName)
         val otherTracks = Tracks.filter { it.id != startTrack.id }.shuffled()
         return listOf(startTrack) + otherTracks
    }
}
