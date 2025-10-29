#ifndef ASTRASTREAM_FLVMUXER_H
#define ASTRASTREAM_FLVMUXER_H

#include <array>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <vector>

namespace astra {

enum class VideoCodecId : uint8_t {
    kH264 = 7,
    kH265 = 12,
};

struct VideoConfig {
    VideoCodecId codec{VideoCodecId::kH264};
    uint32_t width = 0;
    uint32_t height = 0;
    uint32_t fps = 0;
};

struct AudioConfig {
    uint32_t sampleRate = 44100;
    uint8_t channels = 1;
    uint8_t sampleSizeBits = 16;
    std::vector<uint8_t> asc;  // AudioSpecificConfig
};

struct ParsedVideoFrame {
    std::vector<uint8_t> payload;  // length-prefixed NAL units combined
    bool isKeyFrame = false;
    bool hasData() const { return !payload.empty(); }
};

class FlvMuxer {
public:
    FlvMuxer() = default;

    void reset();

    void setVideoConfig(const VideoConfig& config);
    void setAudioConfig(const AudioConfig& config);

    [[nodiscard]] const VideoConfig& videoConfig() const { return videoConfig_; }
    [[nodiscard]] const AudioConfig& audioConfig() const { return audioConfig_; }

    [[nodiscard]] bool videoSequenceReady() const;
    [[nodiscard]] bool audioSequenceReady() const;

    bool hasSentVideoSequence() const { return videoSequenceSent_; }
    bool hasSentAudioSequence() const { return audioSequenceSent_; }
    bool hasSentMetadata() const { return metadataSent_; }

    void markVideoSequenceSent() { videoSequenceSent_ = true; }
    void markAudioSequenceSent() { audioSequenceSent_ = true; }
    void markMetadataSent() { metadataSent_ = true; }

    [[nodiscard]] std::optional<std::vector<uint8_t>> buildMetadataTag() const;
    [[nodiscard]] std::optional<std::vector<uint8_t>> buildVideoSequenceHeader();
    [[nodiscard]] std::optional<std::vector<uint8_t>> buildAudioSequenceHeader() const;

    [[nodiscard]] ParsedVideoFrame parseVideoFrame(const uint8_t* data, size_t size);
    std::vector<uint8_t> buildVideoTag(const ParsedVideoFrame& frame) const;
    std::vector<uint8_t> buildAudioTag(const uint8_t* data, size_t size) const;

private:
    bool parseAnnexbFrame(const uint8_t* data,
                          size_t size,
                          std::vector<uint8_t>& payload,
                          bool& isKeyFrame);
    bool parseHevcFrame(const uint8_t* data,
                        size_t size,
                        std::vector<uint8_t>& payload,
                        bool& isKeyFrame);

    void ensureMetadataDefaults();

    std::vector<uint8_t> buildFlvTag(uint8_t tagType,
                                     const std::vector<uint8_t>& payload) const;
    static void writeTagHeader(std::vector<uint8_t>& buffer,
                               uint8_t tagType,
                               uint32_t dataSize);
    static std::array<uint8_t, 2> buildAudioHeader(const AudioConfig& config, bool isSequence);
    static uint8_t buildVideoHeader(VideoCodecId codec,
                                    bool isKeyFrame,
                                    bool isSequence);

    std::vector<uint8_t> buildAvcDecoderConfigurationRecord() const;
    std::vector<uint8_t> buildHevcDecoderConfigurationRecord() const;

    VideoConfig videoConfig_{};
    AudioConfig audioConfig_{};

    std::vector<uint8_t> sps_;
    std::vector<uint8_t> pps_;
    std::vector<uint8_t> vps_;

    bool metadataSent_ = false;
    bool videoSequenceSent_ = false;
    bool audioSequenceSent_ = false;
};

}  // namespace astra

#endif  // ASTRASTREAM_FLVMUXER_H
