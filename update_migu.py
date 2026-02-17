import requests
import re

def get_migu_url(cid):
    # 保持你现在的原生抓取逻辑，这是最稳的
    return f"http://wwtv.miguvideo.com/migu/live/{cid}/playlist.m3u8"

def get_extra_channels():
    # 抓取你提供的外部有效直播源
    url = "https://raw.githubusercontent.com/hujingguang/ChinaIPTV/main/cn.m3u8"
    try:
        response = requests.get(url, timeout=10)
        if response.status_code == 200:
            # 简单处理 m3u 格式，提取频道名和链接
            content = response.text
            extra_lines = []
            current_name = ""
            for line in content.split('\n'):
                if "#EXTINF" in line:
                    current_name = line.split(',')[-1].strip()
                elif "http" in line and current_name:
                    extra_lines.append(f"{current_name},{line.strip()}")
                    current_name = ""
            return extra_lines
    except:
        return []
    return []

def main():
    # 1. 咪咕原有的频道
    migu_channels = [
        ("CCTV-1", "608807420"),
        ("CCTV-5", "608807428"),
        ("CCTV-5+", "608807435"),
        ("江苏卫视", "608807446"),
        ("湖南卫视", "608807447"),
        ("浙江卫视", "608807448")
    ]
    
    output = ["咪咕直播,#genre#"]
    for name, cid in migu_channels:
        output.append(f"{name},{get_migu_url(cid)}")
    
    # 2. 添加你提供的新直播源分类
    extra = get_extra_channels()
    if extra:
        output.append("其他高清频道,#genre#")
        output.extend(extra)

    with open("migu.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(output))
    print("全频道更新完成")

if __name__ == "__main__":
    main()
