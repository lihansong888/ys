import requests

def get_migu():
    channels = [
        ("CCTV-1", "608807420"),
        ("CCTV-5", "608807428"),
        ("CCTV-5+", "608807435"),
        ("江苏卫视", "608807446"),
        ("湖南卫视", "608807447"),
        ("浙江卫视", "608807448")
    ]
    api_base = "https://live.fanmingming.com/migu/"
    output = []
    for name, cid in channels:
        output.append(f"{name},{api_base}{cid}.m3u8")
    return "\n".join(output)

if __name__ == "__main__":
    content = get_migu()
    with open("migu.txt", "w", encoding="utf-8") as f:
        f.write(content)
    print("migu.txt 更新完成")
